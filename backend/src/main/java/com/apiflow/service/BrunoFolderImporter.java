package com.apiflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.Environment;
import com.apiflow.model.Folder;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class BrunoFolderImporter {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private BrunoFolderImporter() {
	}

	public record BrunoFile(String path, String content) {
	}

	public static RequestCollection importFiles(List<BrunoFile> files) {
		if (files == null || files.isEmpty()) {
			throw new IllegalArgumentException("Choose a Bruno collection folder");
		}
		RequestCollection collection = new RequestCollection();
		collection.setId(java.util.UUID.randomUUID().toString());
		collection.setName("Imported Bruno");
		Map<String, String> folderIds = new LinkedHashMap<>();
		List<BrunoFile> bru = new ArrayList<>();
		for (BrunoFile file : files) {
			String relative = stripRoot(file.path());
			if (relative.isBlank() || relative.startsWith("environments/")) {
				continue;
			}
			if (relative.endsWith("collection.bru")) {
				BrunoParser.applyCollectionMeta(collection, file.content() == null ? "" : file.content());
				continue;
			}
			if (relative.endsWith("bruno.json")) {
				String name = jsonName(file.content());
				if (!name.isBlank()) {
					collection.setName(name);
				}
				continue;
			}
			if (relative.endsWith(".bru")) {
				bru.add(new BrunoFile(relative, file.content() == null ? "" : file.content()));
			}
		}
		for (BrunoFile file : bru) {
			ensureFolders(collection, folderIds, parent(file.path()));
		}
		for (BrunoFile file : bru) {
			if (!isFolder(file.content())) {
				continue;
			}
			String folderPath = parent(file.path());
			String id = folderIds.get(folderPath);
			if (id == null) {
				continue;
			}
			String name = BrunoParser.request(file.content()).getName();
			for (Folder folder : collection.getFolders()) {
				if (id.equals(folder.getId()) && name != null && !name.isBlank() && !"Imported request".equals(name)) {
					folder.setName(name);
				}
			}
		}
		int position = 0;
		for (BrunoFile file : bru) {
			if (isFolder(file.content())) {
				continue;
			}
			ApiRequest request = BrunoParser.request(file.content());
			request.setFolderId(folderIds.getOrDefault(parent(file.path()), ""));
			request.setPosition(position++);
			collection.getRequests().add(request);
		}
		if (collection.getRequests().isEmpty()) {
			throw new IllegalArgumentException("That folder has no Bruno requests");
		}
		return collection;
	}

	public static List<Environment> environments(List<BrunoFile> files) {
		List<Environment> environments = new ArrayList<>();
		if (files == null) {
			return environments;
		}
		for (BrunoFile file : files) {
			String relative = stripRoot(file.path());
			if (!relative.startsWith("environments/") || !relative.endsWith(".bru")) {
				continue;
			}
			String fileName = relative.substring(relative.lastIndexOf('/') + 1);
			String name = fileName.substring(0, fileName.length() - 4);
			Environment environment = new Environment();
			environment.setId(java.util.UUID.randomUUID().toString());
			environment.setName(name.isBlank() ? "Imported" : name);
			Environment parsed = parseEnvironment(file.content());
			environment.setVariables(parsed.getVariables());
			environment.setExternalSecrets(parsed.getExternalSecrets());
			environments.add(environment);
		}
		return environments;
	}

	public static Environment parseEnvironment(String content) {
		Environment environment = new Environment();
		environment.setVariables(parseVars(content));
		environment.setExternalSecrets(parseExternalSecrets(content));
		return environment;
	}

	static List<KeyValue> parseVars(String content) {
		List<KeyValue> variables = new ArrayList<>();
		Set<String> secrets = new LinkedHashSet<>();
		String block = "";
		for (String line : (content == null ? "" : content).split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("vars:secret")) {
				block = "secret";
				continue;
			}
			if (trimmed.equals("vars {") || trimmed.startsWith("vars {")) {
				block = "vars";
				continue;
			}
			if (trimmed.equals("}") || trimmed.equals("]")) {
				block = "";
				continue;
			}
			if ("secret".equals(block)) {
				String name = trimmed.replace(",", "").replace("~", "").trim();
				if (name.isBlank() || "[".equals(name)) {
					continue;
				}
				int colon = name.indexOf(':');
				if (colon > 0) {
					addVar(variables, name.substring(0, colon).trim(), name.substring(colon + 1).trim(), true);
				}
				else {
					secrets.add(name);
				}
				continue;
			}
			if (!"vars".equals(block)) {
				continue;
			}
			int colon = trimmed.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String key = trimmed.substring(0, colon).trim();
			boolean secret = key.startsWith("~");
			if (secret) {
				key = key.substring(1);
			}
			addVar(variables, key, trimmed.substring(colon + 1).trim(), secret);
		}
		for (KeyValue variable : variables) {
			if (secrets.contains(variable.getKey())) {
				variable.setSecret(true);
			}
		}
		return variables;
	}

	static java.util.Map<String, String> parseExternalSecrets(String content) {
		java.util.Map<String, String> secrets = new LinkedHashMap<>();
		String block = "";
		for (String line : (content == null ? "" : content).split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.equals("externalSecrets {") || trimmed.startsWith("externalSecrets {")) {
				block = "externalSecrets";
				continue;
			}
			if ("externalSecrets".equals(block) && (trimmed.equals("}") || trimmed.equals("]"))) {
				block = "";
				continue;
			}
			if (!"externalSecrets".equals(block)) {
				continue;
			}
			int colon = trimmed.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String key = trimmed.substring(0, colon).trim();
			String value = trimmed.substring(colon + 1).trim();
			if (value.endsWith(",")) {
				value = value.substring(0, value.length() - 1).trim();
			}
			secrets.put(key, unquote(value));
		}
		return secrets;
	}

	private static String unquote(String value) {
		if ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private static void addVar(List<KeyValue> variables, String key, String value, boolean secret) {
		if (key == null || key.isBlank()) {
			return;
		}
		KeyValue variable = new KeyValue(key, value, true);
		variable.setSecret(secret);
		variables.add(variable);
	}

	private static void ensureFolders(RequestCollection collection, Map<String, String> folderIds, String directory) {
		if (directory == null || directory.isBlank()) {
			return;
		}
		String[] parts = directory.split("/");
		String current = "";
		String parentId = "";
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			current = current.isEmpty() ? part : current + "/" + part;
			if (!folderIds.containsKey(current)) {
				Folder folder = new Folder();
				folder.setId(java.util.UUID.randomUUID().toString());
				folder.setName(part);
				folder.setParentId(parentId);
				folder.setPosition(collection.getFolders().size());
				collection.getFolders().add(folder);
				folderIds.put(current, folder.getId());
			}
			parentId = folderIds.get(current);
		}
	}

	static String stripRoot(String path) {
		if (path == null) {
			return "";
		}
		String normalized = path.replace('\\', '/');
		int slash = normalized.indexOf('/');
		if (slash < 0) {
			return normalized;
		}
		return normalized.substring(slash + 1);
	}

	private static String parent(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	private static boolean isFolder(String content) {
		return BrunoParser.isFolder(content);
	}

	private static String jsonName(String content) {
		try {
			JsonNode node = MAPPER.readTree(content);
			String name = node.path("name").asString();
			return name == null ? "" : name;
		}
		catch (Exception ex) {
			return "";
		}
	}

}
