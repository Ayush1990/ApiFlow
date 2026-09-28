package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;

public final class CurlParser {

	private static final Set<String> FLAGS = Set.of("-k", "-s", "-S", "-L", "-i", "-v", "--compressed", "--location", "--silent",
			"--insecure", "--http1.1", "--http2", "--globoff");

	private CurlParser() {
	}

	public static RequestCollection collection(String text) {
		List<ApiRequest> requests = parse(text);
		if (requests.isEmpty()) {
			throw new IllegalArgumentException("No curl command found");
		}
		RequestCollection collection = new RequestCollection();
		collection.setId(UUID.randomUUID().toString());
		collection.setName(requests.size() == 1 ? requests.get(0).getName() : "Imported curl");
		int position = 0;
		for (ApiRequest request : requests) {
			request.setPosition(position++);
			collection.getRequests().add(request);
		}
		return collection;
	}

	public static List<ApiRequest> parse(String text) {
		List<ApiRequest> requests = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return requests;
		}
		for (String command : commands(text)) {
			requests.add(one(command));
		}
		return requests;
	}

	private static List<String> commands(String text) {
		String normalized = text.replace("\\\r\n", " ").replace("\\\n", " ");
		List<String> commands = new ArrayList<>();
		StringBuilder current = null;
		for (String line : normalized.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			if (trimmed.startsWith("curl ") || trimmed.equals("curl")) {
				if (current != null && current.length() > 0) {
					commands.add(current.toString());
				}
				current = new StringBuilder(trimmed);
			}
			else if (current != null) {
				current.append(' ').append(trimmed);
			}
		}
		if (current != null && current.length() > 0) {
			commands.add(current.toString());
		}
		return commands;
	}

	private static ApiRequest one(String command) {
		List<String> tokens = tokenize(command);
		ApiRequest request = new ApiRequest();
		request.setId(UUID.randomUUID().toString());
		request.setAuthType("none");
		request.setBodyType("none");
		String method = "";
		String url = "";
		StringBuilder body = new StringBuilder();
		boolean asQuery = false;
		for (int index = 0; index < tokens.size(); index++) {
			String token = tokens.get(index);
			if ("curl".equals(token)) {
				continue;
			}
			if ("-X".equals(token) || "--request".equals(token)) {
				method = next(tokens, ++index);
			}
			else if ("-H".equals(token) || "--header".equals(token)) {
				addHeader(request, next(tokens, ++index));
			}
			else if ("-d".equals(token) || "--data".equals(token) || "--data-raw".equals(token) || "--data-binary".equals(token) || "--data-urlencode".equals(token)) {
				if (body.length() > 0) {
					body.append('&');
				}
				body.append(next(tokens, ++index));
			}
			else if ("-u".equals(token) || "--user".equals(token)) {
				String user = next(tokens, ++index);
				int colon = user.indexOf(':');
				request.setAuthType("basic");
				request.setAuthUsername(colon < 0 ? user : user.substring(0, colon));
				request.setAuthPassword(colon < 0 ? "" : user.substring(colon + 1));
			}
			else if ("-A".equals(token) || "--user-agent".equals(token)) {
				request.getHeaders().add(new KeyValue("User-Agent", next(tokens, ++index), true));
			}
			else if ("--url".equals(token)) {
				url = next(tokens, ++index);
			}
			else if ("-G".equals(token) || "--get".equals(token)) {
				asQuery = true;
			}
			else if (FLAGS.contains(token) || token.startsWith("-")) {
				if (takesValue(token) && index + 1 < tokens.size() && !tokens.get(index + 1).startsWith("-")) {
					index++;
				}
			}
			else if (url.isEmpty()) {
				url = token;
			}
		}
		if (body.length() > 0 && asQuery) {
			url = url + (url.contains("?") ? "&" : "?") + body;
			body.setLength(0);
		}
		if (method.isBlank()) {
			method = body.length() > 0 ? "POST" : "GET";
		}
		request.setMethod(method.toUpperCase(Locale.ROOT));
		request.setUrl(url);
		applyBody(request, body.toString());
		promoteAuthHeader(request);
		request.setName(name(url, method));
		return request;
	}

	private static boolean takesValue(String token) {
		return "-o".equals(token) || "--output".equals(token) || "-w".equals(token) || "--write-out".equals(token)
				|| "-m".equals(token) || "--max-time".equals(token) || "--connect-timeout".equals(token) || "-e".equals(token)
				|| "--referer".equals(token) || "--cookie".equals(token) || "-b".equals(token);
	}

	private static void applyBody(ApiRequest request, String body) {
		if (body.isEmpty()) {
			return;
		}
		String type = header(request, "content-type");
		String trimmed = body.trim();
		if (type.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[")) {
			request.setBodyType("json");
		}
		else {
			request.setBodyType("text");
		}
		request.setBody(body);
	}

	private static void promoteAuthHeader(ApiRequest request) {
		for (KeyValue header : new ArrayList<>(request.getHeaders())) {
			if (!"authorization".equalsIgnoreCase(header.getKey()) || header.getValue() == null) {
				continue;
			}
			String value = header.getValue().trim();
			if (value.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
				request.setAuthType("bearer");
				request.setAuthToken(value.substring(7).trim());
				request.getHeaders().remove(header);
			}
		}
	}

	private static String header(ApiRequest request, String name) {
		for (KeyValue header : request.getHeaders()) {
			if (header.getKey() != null && header.getKey().equalsIgnoreCase(name) && header.getValue() != null) {
				return header.getValue().toLowerCase(Locale.ROOT);
			}
		}
		return "";
	}

	private static void addHeader(ApiRequest request, String raw) {
		int colon = raw.indexOf(':');
		if (colon < 0) {
			return;
		}
		request.getHeaders().add(new KeyValue(raw.substring(0, colon).trim(), raw.substring(colon + 1).trim(), true));
	}

	private static String name(String url, String method) {
		String path = url;
		int scheme = path.indexOf("://");
		if (scheme >= 0) {
			int slash = path.indexOf('/', scheme + 3);
			path = slash < 0 ? "" : path.substring(slash);
		}
		int query = path.indexOf('?');
		if (query >= 0) {
			path = path.substring(0, query);
		}
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		int slash = path.lastIndexOf('/');
		String leaf = slash < 0 ? path : path.substring(slash + 1);
		if (leaf.isBlank()) {
			return method + " request";
		}
		return method.toUpperCase(Locale.ROOT) + " " + leaf;
	}

	private static String next(List<String> tokens, int index) {
		if (index < 0 || index >= tokens.size()) {
			throw new IllegalArgumentException("A curl flag is missing its value");
		}
		return tokens.get(index);
	}

	static List<String> tokenize(String command) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean single = false;
		boolean doub = false;
		for (int index = 0; index < command.length(); index++) {
			char character = command.charAt(index);
			if (single) {
				if (character == '\'') {
					single = false;
				}
				else {
					current.append(character);
				}
				continue;
			}
			if (doub) {
				if (character == '\\' && index + 1 < command.length()) {
					current.append(command.charAt(++index));
				}
				else if (character == '"') {
					doub = false;
				}
				else {
					current.append(character);
				}
				continue;
			}
			if (character == '\'') {
				single = true;
			}
			else if (character == '"') {
				doub = true;
			}
			else if (Character.isWhitespace(character)) {
				if (current.length() > 0) {
					tokens.add(current.toString());
					current.setLength(0);
				}
			}
			else {
				current.append(character);
			}
		}
		if (current.length() > 0) {
			tokens.add(current.toString());
		}
		return tokens;
	}

}
