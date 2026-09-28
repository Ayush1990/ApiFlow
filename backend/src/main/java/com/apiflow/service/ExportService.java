package com.apiflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.Environment;
import com.apiflow.model.Folder;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.Workspace;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ExportService {

	private final ObjectMapper mapper = new ObjectMapper();

	public String exportBruno(RequestCollection collection) {
		StringBuilder out = new StringBuilder();
		out.append("meta {\n  name: ").append(escape(collection.getName())).append("\n  type: collection\n}\n\n");
		for (KeyValue variable : collection.getVariables()) {
			if (variable.getKey() != null && !variable.getKey().isBlank()) {
				out.append("vars {\n  ").append(variable.getKey()).append(": ").append(escape(variable.getValue())).append("\n}\n\n");
			}
		}
		for (ApiRequest request : collection.getRequests()) {
			out.append(BrunoExporter.request(request, folderPath(collection, request.getFolderId()))).append('\n');
		}
		return out.toString();
	}

	public String exportEnvironment(Environment environment) {
		StringBuilder out = new StringBuilder();
		out.append("vars {\n");
		for (KeyValue variable : environment.getVariables()) {
			if (variable.getKey() != null && !variable.getKey().isBlank()) {
				out.append("  ").append(variable.isSecret() ? "~" : "").append(variable.getKey()).append(": ").append(escape(variable.getValue())).append('\n');
			}
		}
		out.append("}\n");
		List<String> secrets = new ArrayList<>();
		for (KeyValue variable : environment.getVariables()) {
			if (variable.isSecret() && variable.getKey() != null && !variable.getKey().isBlank()) {
				secrets.add(variable.getKey());
			}
		}
		if (!secrets.isEmpty()) {
			out.append("vars:secret [\n");
			for (String secret : secrets) {
				out.append("  ").append(secret).append('\n');
			}
			out.append("]\n");
		}
		if (environment.getExternalSecrets() != null && !environment.getExternalSecrets().isEmpty()) {
			out.append("\nexternalSecrets {\n");
			for (var entry : environment.getExternalSecrets().entrySet()) {
				out.append("  ").append(entry.getKey()).append(": ").append(escape(entry.getValue())).append('\n');
			}
			out.append("}\n");
		}
		return out.toString();
	}

	public String exportWorkspace(Workspace workspace) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workspace);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not export workspace");
		}
	}

	public String exportPostman(RequestCollection collection) {
		try {
			ObjectNode root = mapper.createObjectNode();
			ObjectNode info = mapper.createObjectNode();
			info.put("name", collection.getName());
			info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
			root.set("info", info);
			ArrayNode items = root.putArray("item");
			for (ApiRequest request : collection.getRequests()) {
				ObjectNode item = items.addObject();
				item.put("name", request.getName());
				ObjectNode req = item.putObject("request");
				req.put("method", request.getMethod());
				req.put("url", request.getUrl());
				ArrayNode headers = req.putArray("header");
				for (KeyValue header : request.getHeaders()) {
					if (header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
						headers.addObject().put("key", header.getKey()).put("value", header.getValue()).put("type", "text");
					}
				}
				if (!"none".equals(request.getBodyType()) && request.getBody() != null && !request.getBody().isBlank()) {
					req.putObject("body").put("mode", "raw").put("raw", request.getBody());
				}
			}
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not export Postman collection");
		}
	}

	public String exportOpenApi(RequestCollection collection) {
		try {
			ObjectNode root = mapper.createObjectNode();
			root.put("openapi", "3.0.3");
			root.putObject("info").put("title", collection.getName()).put("version", "1.0.0");
			ObjectNode paths = root.putObject("paths");
			for (ApiRequest request : collection.getRequests()) {
				String path = MockServer.pathOf(request.getUrl());
				if (path.isBlank()) {
					continue;
				}
				ObjectNode pathNode = (ObjectNode) paths.get(path);
				if (pathNode == null) {
					pathNode = paths.putObject(path);
				}
				String method = request.getMethod() == null ? "get" : request.getMethod().toLowerCase();
				ObjectNode op = pathNode.putObject(method);
				op.put("summary", request.getName());
				if (request.getDocs() != null && !request.getDocs().isBlank()) {
					op.put("description", request.getDocs());
				}
				if (!request.getExampleBody().isBlank()) {
					op.putObject("responses").putObject("200").put("description", "OK")
						.putObject("content").putObject(request.getExampleContentType().isBlank() ? "application/json" : request.getExampleContentType())
						.putObject("schema").put("example", request.getExampleBody());
				}
			}
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not export OpenAPI");
		}
	}

	private static String folderPath(RequestCollection collection, String folderId) {
		if (folderId == null || folderId.isBlank()) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		String current = folderId;
		while (current != null && !current.isBlank()) {
			for (Folder folder : collection.getFolders()) {
				if (current.equals(folder.getId())) {
					parts.add(0, folder.getName());
					current = folder.getParentId();
					break;
				}
			}
			if (current != null && current.equals(folderId)) {
				break;
			}
		}
		return String.join("/", parts);
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains("\n") || value.contains("\"")) {
			return "`" + value.replace("\u0060", "\\\u0060") + "`";
		}
		return value;
	}

	public static final class BrunoExporter {
		private BrunoExporter() {
		}

		static String request(ApiRequest request, String folderPath) {
			StringBuilder out = new StringBuilder();
			String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
			String metaType = "GRPC".equals(method) ? "grpc" : "http";
			out.append("meta {\n  name: ").append(escape(request.getName())).append("\n  type: ").append(metaType);
			if (request.getTags() != null && !request.getTags().isEmpty()) {
				out.append("\n  tags: ").append(String.join(", ", request.getTags()));
			}
			out.append("\n}\n\n");
			if (!request.getParams().isEmpty()) {
				out.append("params {\n");
				appendPairs(out, request.getParams());
				out.append("}\n\n");
			}
			if (!request.getHeaders().isEmpty()) {
				out.append("headers {\n");
				appendPairs(out, request.getHeaders());
				out.append("}\n\n");
			}
			if (request.getTags() != null && !request.getTags().isEmpty()) {
				out.append("tags {\n");
				for (String tag : request.getTags()) {
					if (tag != null && !tag.isBlank()) {
						out.append("  ").append(tag.trim()).append('\n');
					}
				}
				out.append("}\n\n");
			}
			appendAuth(out, request);
			out.append((method.equals("GRPC") ? "grpc" : method.toLowerCase())).append(" {\n");
			out.append("  url: ").append(escape(request.getUrl())).append('\n');
			out.append("  body: ").append(request.getBodyType() == null ? "none" : request.getBodyType()).append('\n');
			out.append("  auth: ").append(request.getAuthType() == null ? "none" : request.getAuthType()).append('\n');
			out.append("}\n");
			if (request.getBody() != null && !request.getBody().isBlank() && !"none".equals(request.getBodyType())) {
				out.append("\nbody:").append(request.getBodyType()).append(" {\n").append(request.getBody()).append("\n}\n");
			}
			if ("GRPC".equals(method) && request.getExtras() != null && !request.getExtras().getGrpcService().isBlank()) {
				out.append("\ngrpc {\n  method: ").append(escape(request.getExtras().getGrpcService()));
				if (!request.getExtras().getGrpcMode().isBlank()) {
					out.append("\n  mode: ").append(request.getExtras().getGrpcMode());
				}
				if (!request.getExtras().getGrpcProto().isBlank()) {
					out.append("\n  proto: ").append(escape(request.getExtras().getGrpcProto()));
				}
				out.append("\n}\n");
			}
			if (request.getTimeoutSeconds() > 0 && request.getTimeoutSeconds() != 30) {
				out.append("\nsettings {\n  timeout: ").append(request.getTimeoutSeconds() * 1000).append("\n}\n");
			}
			if (request.getDocs() != null && !request.getDocs().isBlank()) {
				out.append("\ndocs {\n").append(request.getDocs()).append("\n}\n");
			}
			if (request.getPreRequestScript() != null && !request.getPreRequestScript().isBlank()) {
				out.append("\nscript:pre-request {\n").append(request.getPreRequestScript()).append("\n}\n");
			}
			if (request.getPostResponseScript() != null && !request.getPostResponseScript().isBlank()) {
				out.append("\nscript:post-response {\n").append(request.getPostResponseScript()).append("\n}\n");
			}
			if (request.getAssertions() != null && !request.getAssertions().isEmpty()) {
				out.append("\nassert {\n");
				for (com.apiflow.model.Assertion assertion : request.getAssertions()) {
					if (assertion.getType() == null || assertion.getType().isBlank()) {
						continue;
					}
					appendAssertion(out, assertion);
				}
				out.append("}\n");
			}
			if ("multipart".equals(request.getBodyType()) && !request.getForm().isEmpty()) {
				out.append("\nbody:multipart {\n");
				appendPairs(out, request.getForm());
				out.append("}\n");
			}
			return out.toString();
		}

		private static void appendPairs(StringBuilder out, List<KeyValue> pairs) {
			for (KeyValue pair : pairs) {
				if (pair.getKey() == null || pair.getKey().isBlank()) {
					continue;
				}
				out.append("  ").append(pair.isEnabled() ? "" : "~").append(pair.getKey()).append(": ").append(escape(pair.getValue())).append('\n');
			}
		}

		private static void appendAuth(StringBuilder out, ApiRequest request) {
			String auth = request.getAuthType() == null ? "none" : request.getAuthType();
			if ("none".equals(auth) || "inherit".equals(auth)) {
				return;
			}
			if ("bearer".equals(auth)) {
				out.append("\nauth:bearer {\n  token: ").append(escape(request.getAuthToken())).append("\n}\n\n");
			}
			else if ("basic".equals(auth)) {
				out.append("\nauth:basic {\n  username: ").append(escape(request.getAuthUsername()))
					.append("\n  password: ").append(escape(request.getAuthPassword())).append("\n}\n\n");
			}
			else if ("apikey".equals(auth)) {
				out.append("\nauth:apikey {\n  key: ").append(escape(request.getApiKeyName()))
					.append("\n  value: ").append(escape(request.getApiKeyValue()))
					.append("\n  in: ").append(request.getApiKeyIn() == null ? "header" : request.getApiKeyIn()).append("\n}\n\n");
			}
			else if ("digest".equals(auth) || "ntlm".equals(auth)) {
				out.append("\nauth:").append(auth).append(" {\n  username: ").append(escape(request.getAuthUsername()))
					.append("\n  password: ").append(escape(request.getAuthPassword())).append("\n}\n\n");
			}
			else if ("oauth2".equals(auth) || "oauth-2".equals(auth)) {
				var extras = request.getExtras();
				out.append("\nauth:oauth2 {\n  grant_type: ").append(extras.getOauthGrant())
					.append("\n  access_token_url: ").append(escape(extras.getOauthTokenUrl()))
					.append("\n  auth_url: ").append(escape(extras.getOauthAuthUrl()))
					.append("\n  client_id: ").append(escape(extras.getOauthClientId()))
					.append("\n  client_secret: ").append(escape(extras.getOauthClientSecret()))
					.append("\n  scope: ").append(escape(extras.getOauthScope()));
				if (extras.getOauthUsername() != null && !extras.getOauthUsername().isBlank()) {
					out.append("\n  username: ").append(escape(extras.getOauthUsername()));
				}
				if (extras.getOauthPassword() != null && !extras.getOauthPassword().isBlank()) {
					out.append("\n  password: ").append(escape(extras.getOauthPassword()));
				}
				out.append("\n}\n\n");
			}
			else if ("aws".equals(auth) || "awsv4".equals(auth) || "aws-sigv4".equals(auth)) {
				var extras = request.getExtras();
				out.append("\nauth:awsv4 {\n  access_key: ").append(escape(extras.getAwsAccessKey()))
					.append("\n  secret_key: ").append(escape(extras.getAwsSecretKey()))
					.append("\n  region: ").append(escape(extras.getAwsRegion()))
					.append("\n  service: ").append(escape(extras.getAwsService())).append("\n}\n\n");
			}
			else if ("oauth1".equals(auth)) {
				var extras = request.getExtras();
				out.append("\nauth:oauth1 {\n  consumer_key: ").append(escape(extras.getOauth1ConsumerKey()))
					.append("\n  consumer_secret: ").append(escape(extras.getOauth1ConsumerSecret()))
					.append("\n  token: ").append(escape(extras.getOauth1Token()))
					.append("\n  token_secret: ").append(escape(extras.getOauth1TokenSecret())).append("\n}\n\n");
			}
			else if ("edgegrid".equals(auth) || "akamai".equals(auth)) {
				var extras = request.getExtras();
				out.append("\nauth:edgegrid {\n  client_token: ").append(escape(extras.getEdgeGridClientToken()))
					.append("\n  client_secret: ").append(escape(extras.getEdgeGridClientSecret()))
					.append("\n  access_token: ").append(escape(extras.getEdgeGridAccessToken()))
					.append("\n  host: ").append(escape(extras.getEdgeGridHost())).append("\n}\n\n");
			}
		}

		private static void appendAssertion(StringBuilder out, com.apiflow.model.Assertion assertion) {
			String type = assertion.getType();
			if (type == null || type.isBlank()) {
				return;
			}
			if (type.endsWith(":contains")) {
				out.append("  ").append(type, 0, type.length() - 9).append(": contains ").append(escape(assertion.getExpected())).append('\n');
			}
			else if ("jsonEquals".equals(type)) {
				out.append("  json: eq ").append(assertion.getPath() == null ? "" : assertion.getPath()).append(' ')
					.append(escape(assertion.getExpected())).append('\n');
			}
			else if ("header".equals(type)) {
				out.append("  header: eq ").append(assertion.getPath() == null ? "" : assertion.getPath()).append(' ')
					.append(escape(assertion.getExpected())).append('\n');
			}
			else if ("exists".equals(type) || "isArray".equals(type)) {
				out.append("  ").append(type).append(": eq ").append(escape(assertion.getPath())).append('\n');
			}
			else if ("type".equals(type)) {
				out.append("  type: eq ").append(assertion.getPath()).append(' ').append(escape(assertion.getExpected())).append('\n');
			}
			else {
				out.append("  ").append(type).append(": eq ").append(escape(assertion.getExpected())).append('\n');
			}
		}
	}

}
