package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.Assertion;
import com.apiflow.model.Folder;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.RequestExtras;

public final class BrunoParser {

	private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete", "head", "options", "grpc", "ws", "sse");

	private BrunoParser() {
	}

	public static boolean looksLike(String content) {
		String trimmed = content == null ? "" : content.trim();
		return trimmed.startsWith("meta {") || trimmed.contains("\nmeta {") || BruYamlMigrator.looksLikeYamlV3(trimmed);
	}

	public static ApiRequest request(String document) {
		if (BruYamlMigrator.looksLikeYamlV3(document)) {
			document = BruYamlMigrator.toBru(document);
		}
		return one(document);
	}

	public static boolean isFolder(String content) {
		return content != null && content.matches("(?s).*\\btype\\s*:\\s*folder\\b.*");
	}

	public static void applyCollectionMeta(RequestCollection collection, String document) {
		if (BruYamlMigrator.looksLikeYamlV3(document)) {
			document = BruYamlMigrator.toBru(document);
		}
		for (Block block : blocks(document)) {
			String name = block.name.toLowerCase(Locale.ROOT);
			if ("meta".equals(name)) {
				String collectionName = field(block.body, "name", "");
				if (!collectionName.isBlank()) {
					collection.setName(collectionName);
				}
			}
			else if ("vars".equals(name)) {
				collection.setVariables(BrunoFolderImporter.parseVars("vars {\n" + block.body + "\n}"));
			}
			else if ("script:pre-request".equals(name)) {
				collection.setPreRequestScript(block.body.trim());
			}
			else if ("script:post-response".equals(name)) {
				collection.setPostResponseScript(block.body.trim());
			}
		}
	}

	public static void applyFolderMeta(Folder folder, String document) {
		if (folder == null) {
			return;
		}
		for (Block block : blocks(document)) {
			String name = block.name.toLowerCase(Locale.ROOT);
			if ("meta".equals(name)) {
				String folderName = field(block.body, "name", "");
				if (!folderName.isBlank()) {
					folder.setName(folderName);
				}
			}
			else if ("vars".equals(name)) {
				folder.setVariables(BrunoFolderImporter.parseVars("vars {\n" + block.body + "\n}"));
			}
			else if ("script:pre-request".equals(name)) {
				folder.setPreRequestScript(block.body.trim());
			}
			else if ("script:post-response".equals(name)) {
				folder.setPostResponseScript(block.body.trim());
			}
		}
	}

	public static RequestCollection collection(String content) {
		String[] parts = content.split("(?m)^meta \\{");
		RequestCollection collection = new RequestCollection();
		collection.setId(UUID.randomUUID().toString());
		int position = 0;
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			ApiRequest request = one("meta {" + part);
			request.setPosition(position++);
			collection.getRequests().add(request);
		}
		if (collection.getRequests().isEmpty()) {
			throw new IllegalArgumentException("No Bruno request found");
		}
		collection.setName(collection.getRequests().size() == 1 ? collection.getRequests().get(0).getName() : "Imported Bruno");
		return collection;
	}

	private static ApiRequest one(String document) {
		ApiRequest request = new ApiRequest();
		request.setId(UUID.randomUUID().toString());
		request.setMethod("GET");
		request.setAuthType("none");
		request.setBodyType("none");
		for (Block block : blocks(document)) {
			String name = block.name.toLowerCase(Locale.ROOT);
			if ("meta".equals(name)) {
				request.setName(field(block.body, "name", "Imported request"));
				String type = field(block.body, "type", "http");
				if ("grpc".equalsIgnoreCase(type)) {
					request.setMethod("GRPC");
				}
				String tags = field(block.body, "tags", "");
				if (!tags.isBlank()) {
					request.setTags(parseTags(tags));
				}
			}
			else if (METHODS.contains(name)) {
				request.setMethod(name.toUpperCase(Locale.ROOT));
				request.setUrl(unquote(field(block.body, "url", "")));
				String bodyKind = field(block.body, "body", "none");
				if (!"none".equals(bodyKind)) {
					request.setBodyType(bodyKind);
				}
				String auth = field(block.body, "auth", "none");
				if (!"none".equals(auth)) {
					request.setAuthType(auth);
				}
			}
			else if ("headers".equals(name)) {
				pairs(block.body, request.getHeaders());
			}
			else if ("params".equals(name) || "query".equals(name)) {
				pairs(block.body, request.getParams());
			}
			else if ("tags".equals(name)) {
				request.setTags(parseTagBlock(block.body));
			}
			else if ("docs".equals(name)) {
				request.setDocs(block.body.trim());
			}
			else if ("settings".equals(name)) {
				String timeout = field(block.body, "timeout", "");
				if (!timeout.isBlank()) {
					try {
						request.setTimeoutSeconds(Integer.parseInt(timeout.replaceAll("[^0-9]", "")));
					}
					catch (NumberFormatException ignored) {
					}
				}
			}
			else if ("grpc".equals(name)) {
				request.setMethod("GRPC");
				request.getExtras().setGrpcService(field(block.body, "method", field(block.body, "service", "")));
				request.getExtras().setGrpcProto(block.body.contains("proto") ? extractMultiline(block.body, "proto") : request.getExtras().getGrpcProto());
				String mode = field(block.body, "mode", "unary");
				request.getExtras().setGrpcMode(mode);
				request.getExtras().setGrpcStream("server".equalsIgnoreCase(mode));
			}
			else if (name.startsWith("auth:")) {
				parseAuthBlock(name.substring(5), block.body, request);
			}
			else if ("assert".equals(name) || name.startsWith("assert:")) {
				parseAssertions(block.body, request);
			}
			else if ("multipart".equals(name) || "body:multipart".equals(name)) {
				request.setBodyType("multipart");
				pairs(block.body, request.getForm());
			}
			else if (name.startsWith("body:")) {
				request.setBodyType(name.substring(5));
				request.setBody(block.body.trim());
			}
			else if ("script:pre-request".equals(name)) {
				request.setPreRequestScript(block.body.trim());
			}
			else if ("script:post-response".equals(name)) {
				request.setPostResponseScript(block.body.trim());
			}
		}
		if (request.getName() == null || request.getName().isBlank()) {
			request.setName("Imported request");
		}
		return request;
	}

	private static void parseAuthBlock(String kind, String body, ApiRequest request) {
		String normalized = kind.toLowerCase(Locale.ROOT);
		request.setAuthType(normalized);
		RequestExtras extras = request.getExtras();
		if ("bearer".equals(normalized)) {
			request.setAuthToken(unquote(field(body, "token", "")));
		}
		else if ("basic".equals(normalized)) {
			request.setAuthUsername(unquote(field(body, "username", "")));
			request.setAuthPassword(unquote(field(body, "password", "")));
		}
		else if ("apikey".equals(normalized)) {
			request.setApiKeyName(unquote(field(body, "key", "")));
			request.setApiKeyValue(unquote(field(body, "value", "")));
			request.setApiKeyIn(field(body, "in", "header"));
		}
		else if ("digest".equals(normalized)) {
			request.setAuthUsername(unquote(field(body, "username", "")));
			request.setAuthPassword(unquote(field(body, "password", "")));
		}
		else if ("ntlm".equals(normalized)) {
			request.setAuthUsername(unquote(field(body, "username", "")));
			request.setAuthPassword(unquote(field(body, "password", "")));
		}
		else if ("oauth2".equals(normalized) || "oauth-2".equals(normalized)) {
			extras.setOauthGrant(field(body, "grant_type", field(body, "grant", extras.getOauthGrant())));
			extras.setOauthTokenUrl(unquote(field(body, "access_token_url", field(body, "token_url", ""))));
			extras.setOauthAuthUrl(unquote(field(body, "auth_url", field(body, "authorization_url", ""))));
			extras.setOauthClientId(unquote(field(body, "client_id", "")));
			extras.setOauthClientSecret(unquote(field(body, "client_secret", "")));
			extras.setOauthScope(unquote(field(body, "scope", "")));
			extras.setOauthUsername(unquote(field(body, "username", "")));
			extras.setOauthPassword(unquote(field(body, "password", "")));
		}
		else if ("aws".equals(normalized) || "awsv4".equals(normalized) || "aws-sigv4".equals(normalized)) {
			extras.setAwsAccessKey(unquote(field(body, "access_key", field(body, "accessKey", ""))));
			extras.setAwsSecretKey(unquote(field(body, "secret_key", field(body, "secretKey", ""))));
			extras.setAwsRegion(unquote(field(body, "region", extras.getAwsRegion())));
			extras.setAwsService(unquote(field(body, "service", extras.getAwsService())));
		}
		else if ("oauth1".equals(normalized)) {
			extras.setOauth1ConsumerKey(unquote(field(body, "consumer_key", field(body, "consumerKey", ""))));
			extras.setOauth1ConsumerSecret(unquote(field(body, "consumer_secret", field(body, "consumerSecret", ""))));
			extras.setOauth1Token(unquote(field(body, "token", field(body, "access_token", ""))));
			extras.setOauth1TokenSecret(unquote(field(body, "token_secret", field(body, "access_token_secret", ""))));
		}
		else if ("edgegrid".equals(normalized) || "akamai".equals(normalized)) {
			extras.setEdgeGridClientToken(unquote(field(body, "client_token", field(body, "clientToken", ""))));
			extras.setEdgeGridClientSecret(unquote(field(body, "client_secret", field(body, "clientSecret", ""))));
			extras.setEdgeGridAccessToken(unquote(field(body, "access_token", field(body, "accessToken", ""))));
			extras.setEdgeGridHost(unquote(field(body, "host", "")));
		}
	}

	private static void parseAssertions(String body, ApiRequest request) {
		for (String line : body.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("//")) {
				continue;
			}
			int colon = trimmed.indexOf(':');
			if (colon < 0) {
				continue;
			}
			Assertion assertion = new Assertion();
			assertion.setType(trimmed.substring(0, colon).trim());
			String rest = trimmed.substring(colon + 1).trim();
			if (rest.startsWith("eq ")) {
				String value = rest.substring(3).trim();
				if ("json".equals(assertion.getType()) || "jsonEquals".equals(assertion.getType())) {
					int space = value.indexOf(' ');
					if (space > 0) {
						assertion.setType("jsonEquals");
						assertion.setPath(value.substring(0, space).trim());
						assertion.setExpected(unquote(value.substring(space + 1).trim()));
					}
					else {
						assertion.setExpected(value);
					}
				}
				else if ("header".equals(assertion.getType())) {
					int space = value.indexOf(' ');
					if (space > 0) {
						assertion.setPath(value.substring(0, space).trim());
						assertion.setExpected(unquote(value.substring(space + 1).trim()));
					}
					else {
						assertion.setExpected(value);
					}
				}
				else if ("exists".equals(assertion.getType()) || "type".equals(assertion.getType()) || "isArray".equals(assertion.getType())) {
					int space = value.indexOf(' ');
					if ("type".equals(assertion.getType()) && space > 0) {
						assertion.setPath(value.substring(0, space).trim());
						assertion.setExpected(value.substring(space + 1).trim());
					}
					else {
						assertion.setPath(value);
						assertion.setExpected(value);
					}
				}
				else {
					assertion.setExpected(unquote(value));
				}
			}
			else if (rest.startsWith("contains ")) {
				assertion.setType(assertion.getType() + ":contains");
				assertion.setExpected(rest.substring(9).trim());
			}
			else {
				assertion.setExpected(rest);
			}
			request.getAssertions().add(assertion);
		}
	}

	private static List<String> parseTags(String inline) {
		List<String> tags = new ArrayList<>();
		for (String part : inline.split("[,\\s]+")) {
			if (!part.isBlank()) {
				tags.add(part.trim());
			}
		}
		return tags;
	}

	private static List<String> parseTagBlock(String body) {
		List<String> tags = new ArrayList<>();
		for (String line : body.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("//")) {
				continue;
			}
			if (trimmed.startsWith("-")) {
				tags.add(trimmed.substring(1).trim());
			}
			else {
				tags.add(trimmed.replace(",", "").trim());
			}
		}
		return tags;
	}

	private static String extractMultiline(String body, String key) {
		int index = body.toLowerCase(Locale.ROOT).indexOf(key.toLowerCase(Locale.ROOT) + ":");
		if (index < 0) {
			return "";
		}
		return body.substring(index + key.length() + 1).trim();
	}

	private static void pairs(String body, List<KeyValue> target) {
		for (String line : body.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
				continue;
			}
			boolean disabled = trimmed.startsWith("~");
			if (disabled) {
				trimmed = trimmed.substring(1).trim();
			}
			int colon = trimmed.indexOf(':');
			if (colon < 0) {
				continue;
			}
			target.add(new KeyValue(trimmed.substring(0, colon).trim(), unquote(trimmed.substring(colon + 1).trim()), !disabled));
		}
	}

	private static String field(String body, String key, String fallback) {
		for (String line : body.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.toLowerCase(Locale.ROOT).startsWith(key.toLowerCase(Locale.ROOT) + ":")) {
				return trimmed.substring(trimmed.indexOf(':') + 1).trim();
			}
		}
		return fallback;
	}

	private static String unquote(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		if ((trimmed.startsWith("`") && trimmed.endsWith("`")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	static List<Block> blocks(String text) {
		List<Block> found = new ArrayList<>();
		int index = 0;
		while (index < text.length()) {
			int brace = text.indexOf('{', index);
			if (brace < 0) {
				break;
			}
			String name = text.substring(index, brace).trim();
			int newline = name.lastIndexOf('\n');
			if (newline >= 0) {
				name = name.substring(newline + 1).trim();
			}
			int depth = 0;
			int end = brace;
			for (; end < text.length(); end++) {
				char character = text.charAt(end);
				if (character == '{') {
					depth++;
				}
				else if (character == '}') {
					depth--;
					if (depth == 0) {
						end++;
						break;
					}
				}
			}
			if (!name.isBlank()) {
				found.add(new Block(name, text.substring(brace + 1, Math.max(brace + 1, end - 1))));
			}
			index = end;
		}
		return found;
	}

	record Block(String name, String body) {
	}

}
