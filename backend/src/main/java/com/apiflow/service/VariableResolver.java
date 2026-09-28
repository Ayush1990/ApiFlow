package com.apiflow.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.Environment;
import com.apiflow.model.Folder;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.Workspace;

public final class VariableResolver {

	private static final Pattern DYNAMIC = Pattern.compile("\\$([a-zA-Z]+)");

	private VariableResolver() {
	}

	public static Map<String, String> resolve(Workspace workspace, RequestCollection collection, ApiRequest request, Environment environment, Map<String, String> extra, SecretManagerService secrets) {
		return resolve(workspace, collection, request, environment, extra, secrets, "");
	}

	public static Map<String, String> resolve(Workspace workspace, RequestCollection collection, ApiRequest request, Environment environment, Map<String, String> extra, SecretManagerService secrets, String selectedGlobalEnvironmentId) {
		Map<String, String> variables = new LinkedHashMap<>();
		if (workspace != null) {
			putAll(variables, workspace.getVariables());
			if (selectedGlobalEnvironmentId != null && !selectedGlobalEnvironmentId.isBlank()) {
				for (Environment global : workspace.getEnvironments()) {
					if (selectedGlobalEnvironmentId.equals(global.getId())) {
						putAll(variables, global.getVariables());
						break;
					}
				}
			}
			else {
				for (Environment global : workspace.getEnvironments()) {
					if (global.isGlobal()) {
						putAll(variables, global.getVariables());
					}
				}
			}
			for (Map.Entry<String, String> entry : processEnv().entrySet()) {
				if (!variables.containsKey(entry.getKey())) {
					variables.put(entry.getKey(), entry.getValue());
				}
			}
		}
		if (collection != null) {
			putAll(variables, collection.getVariables());
			for (Folder folder : folderChain(collection, request == null ? "" : request.getFolderId())) {
				putAll(variables, folder.getVariables());
			}
		}
		if (request != null) {
			putAll(variables, request.getVariables());
		}
		if (environment != null) {
			putAll(variables, environment.getVariables());
		}
		else if (workspace != null && collection != null) {
			for (Environment scoped : workspace.getEnvironments()) {
				if (!scoped.isGlobal() && collection.getId().equals(scoped.getCollectionId())) {
					putAll(variables, scoped.getVariables());
				}
			}
		}
		if (extra != null) {
			for (Map.Entry<String, String> entry : extra.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isBlank()) {
					variables.put(entry.getKey().trim(), entry.getValue() == null ? "" : entry.getValue());
				}
			}
		}
		applyDynamic(variables);
		if (secrets != null && workspace != null && workspace.getSettings() != null) {
			for (Map.Entry<String, String> entry : new LinkedHashMap<>(variables).entrySet()) {
				variables.put(entry.getKey(), SecretRefs.resolveText(entry.getValue(), secrets, workspace.getSettings().getSecretManager()));
			}
		}
		return variables;
	}

	public static Map<String, String> resolve(Workspace workspace, RequestCollection collection, ApiRequest request, Environment environment, Map<String, String> extra) {
		return resolve(workspace, collection, request, environment, extra, null);
	}

	public static String interpolate(String input, Map<String, String> variables) {
		if (input == null || input.isEmpty()) {
			return "";
		}
		String current = input;
		for (int pass = 0; pass < 4; pass++) {
			String next = RequestExecutor.interpolate(current, variables);
			next = expandDynamic(next, variables);
			if (next.equals(current)) {
				break;
			}
			current = next;
		}
		return current;
	}

	private static void applyDynamic(Map<String, String> variables) {
		for (Map.Entry<String, String> entry : new LinkedHashMap<>(variables).entrySet()) {
			variables.put(entry.getKey(), expandDynamic(entry.getValue(), variables));
		}
	}

	static String expandDynamic(String value, Map<String, String> variables) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String current = value;
		Matcher braced = Pattern.compile("\\{\\{\\s*\\$([a-zA-Z0-9]+)\\s*\\}\\}").matcher(current);
		StringBuffer bracedOut = new StringBuffer();
		while (braced.find()) {
			braced.appendReplacement(bracedOut, Matcher.quoteReplacement(dynamicToken(braced.group(1))));
		}
		braced.appendTail(bracedOut);
		current = bracedOut.toString();
		Matcher vault = Pattern.compile("\\{\\{\\s*vault:([^}]+)\\s*\\}\\}").matcher(current);
		StringBuffer vaultOut = new StringBuffer();
		while (vault.find()) {
			vault.appendReplacement(vaultOut, Matcher.quoteReplacement(VaultStore.get(vault.group(1).trim())));
		}
		vault.appendTail(vaultOut);
		current = vaultOut.toString();
		Matcher matcher = DYNAMIC.matcher(current);
		StringBuffer out = new StringBuffer();
		while (matcher.find()) {
			String token = matcher.group(1);
			String replacement = dynamicToken(token);
			if (replacement.equals("$" + token) && !isKnownDynamic(token)) {
				replacement = matcher.group(0);
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static boolean isKnownDynamic(String token) {
		String key = token == null ? "" : token.toLowerCase();
		return switch (key) {
			case "uuid", "guid", "randomuuid", "timestamp", "isotimestamp", "randomint", "randomemail", "randomfirstname" -> true;
			default -> false;
		};
	}

	private static String dynamicToken(String token) {
		String key = token == null ? "" : token.toLowerCase();
		return switch (key) {
			case "uuid", "guid", "randomuuid" -> UUID.randomUUID().toString();
			case "timestamp" -> String.valueOf(Instant.now().toEpochMilli());
			case "isotimestamp" -> Instant.now().toString();
			case "randomint" -> String.valueOf((int) (Math.random() * 100000));
			case "randomemail" -> "user" + ((int) (Math.random() * 100000)) + "@example.com";
			case "randomfirstname" -> List.of("Ada", "Grace", "Alan", "Lin").get((int) (Math.random() * 4));
			default -> "$" + token;
		};
	}

	private static Map<String, String> processEnv() {
		Map<String, String> map = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
			map.put("process.env." + entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
		}
		return map;
	}

	private static void putAll(Map<String, String> variables, List<KeyValue> source) {
		if (source == null) {
			return;
		}
		for (KeyValue variable : source) {
			if (variable != null && variable.isEnabled() && variable.getKey() != null && !variable.getKey().isBlank()) {
				variables.put(variable.getKey().trim(), variable.getValue() == null ? "" : variable.getValue());
			}
		}
	}

	static List<Folder> folderChain(RequestCollection collection, String folderId) {
		List<Folder> chain = new java.util.ArrayList<>();
		String current = folderId == null ? "" : folderId;
		java.util.Set<String> seen = new java.util.LinkedHashSet<>();
		while (current != null && !current.isBlank() && !seen.contains(current)) {
			seen.add(current);
			Folder found = null;
			for (Folder folder : collection.getFolders()) {
				if (current.equals(folder.getId())) {
					found = folder;
					break;
				}
			}
			if (found == null) {
				break;
			}
			chain.add(0, found);
			current = found.getParentId();
		}
		return chain;
	}

}
