package com.apiflow.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class OpenApiSyncService {

	private final ObjectMapper mapper = new ObjectMapper();

	public RequestCollection syncFromSpec(RequestCollection collection, String openApiContent) {
		return syncFromSpec(collection, openApiContent, "additive", false);
	}

	public RequestCollection syncFromSpec(RequestCollection collection, String openApiContent, String mode, boolean deleteStale) {
		if (openApiContent == null || openApiContent.isBlank()) {
			throw new IllegalArgumentException("OpenAPI spec is empty");
		}
		JsonNode root = parse(openApiContent);
		try {
			collection.setOpenApiSpec(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
		}
		catch (Exception ex) {
			collection.setOpenApiSpec(openApiContent);
		}
		JsonNode paths = root.path("paths");
		if (!paths.isObject()) {
			return collection;
		}
		String syncMode = mode == null || mode.isBlank() ? "additive" : mode.trim().toLowerCase();
		Set<String> seen = new HashSet<>();
		for (var pathEntry : paths.properties()) {
			String path = pathEntry.getKey();
			JsonNode pathNode = pathEntry.getValue();
			for (var methodEntry : pathNode.properties()) {
				String method = methodEntry.getKey();
				if ("parameters".equals(method) || "$ref".equals(method) || "servers".equals(method)) {
					continue;
				}
				JsonNode op = methodEntry.getValue();
				if (!op.isObject()) {
					continue;
				}
				String key = method.toUpperCase() + " " + path;
				seen.add(key);
				ApiRequest existing = findRequest(collection, method, path);
				if (existing != null) {
					if ("additive".equals(syncMode)) {
						continue;
					}
					applyOperation(existing, method, path, op, root);
				}
				else {
					ApiRequest request = new ApiRequest();
					request.setId(UUID.randomUUID().toString());
					applyOperation(request, method, path, op, root);
					request.setPosition(collection.getRequests().size());
					collection.getRequests().add(request);
				}
			}
		}
		if (deleteStale && ("update".equals(syncMode) || "replace".equals(syncMode))) {
			collection.getRequests().removeIf(request -> {
				if (request.getUrl() == null || !request.getUrl().contains("{{baseUrl}}")) {
					return false;
				}
				String key = request.getMethod() + " " + MockServer.pathOf(request.getUrl());
				return !seen.contains(key);
			});
		}
		return collection;
	}

	public OpenApiDiff diff(RequestCollection collection, String openApiContent) {
		JsonNode root = parse(openApiContent);
		List<String> added = new ArrayList<>();
		List<String> updated = new ArrayList<>();
		List<String> removed = new ArrayList<>();
		Set<String> specKeys = new HashSet<>();
		JsonNode paths = root.path("paths");
		if (paths.isObject()) {
			for (var pathEntry : paths.properties()) {
				for (var methodEntry : pathEntry.getValue().properties()) {
					String method = methodEntry.getKey();
					if ("parameters".equals(method) || "$ref".equals(method)) {
						continue;
					}
					if (!methodEntry.getValue().isObject()) {
						continue;
					}
					String key = method.toUpperCase() + " " + pathEntry.getKey();
					specKeys.add(key);
					if (findRequest(collection, method, pathEntry.getKey()) == null) {
						added.add(key);
					}
					else {
						updated.add(key);
					}
				}
			}
		}
		for (ApiRequest request : collection.getRequests()) {
			String key = request.getMethod() + " " + MockServer.pathOf(request.getUrl());
			if (!specKeys.contains(key) && request.getUrl() != null && request.getUrl().contains("{{baseUrl}}")) {
				removed.add(key);
			}
		}
		return new OpenApiDiff(added, updated, removed);
	}

	public String exportMerged(RequestCollection collection) {
		if (collection.getOpenApiSpec() != null && !collection.getOpenApiSpec().isBlank()) {
			try {
				JsonNode root = mapper.readTree(collection.getOpenApiSpec());
				if (root.isObject()) {
					ObjectNode object = (ObjectNode) root;
					if (!object.has("info")) {
						object.putObject("info").put("title", collection.getName()).put("version", "1.0.0");
					}
					return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
				}
			}
			catch (Exception ignored) {
				// Fall back to generated export.
			}
		}
		return new ExportService().exportOpenApi(collection);
	}

	private void applyOperation(ApiRequest request, String method, String path, JsonNode op, JsonNode root) {
		String summary = text(op.path("summary"), method.toUpperCase() + " " + path);
		request.setName(summary);
		request.setMethod(method.toUpperCase());
		request.setUrl("{{baseUrl}}" + path);
		request.setBodyType("none");
		request.setDocs(text(op.path("description"), ""));
		importParameters(request, op, root.path("paths").path(path));
		importSecurity(request, op, root);
	}

	private void importParameters(ApiRequest request, JsonNode op, JsonNode pathNode) {
		List<KeyValue> params = request.getParams();
		params.clear();
		appendParameters(params, pathNode.path("parameters"));
		appendParameters(params, op.path("parameters"));
	}

	private void appendParameters(List<KeyValue> params, JsonNode parameters) {
		if (!parameters.isArray()) {
			return;
		}
		for (JsonNode parameter : parameters) {
			if (!parameter.isObject()) {
				continue;
			}
			String name = text(parameter.path("name"), "");
			if (name.isBlank()) {
				continue;
			}
			String value = text(parameter.path("example"), text(parameter.path("schema").path("default"), ""));
			params.add(new KeyValue(name, value, true));
		}
	}

	private void importSecurity(ApiRequest request, JsonNode op, JsonNode root) {
		JsonNode security = op.path("security");
		if (!security.isArray() || security.isEmpty()) {
			return;
		}
		JsonNode first = security.get(0);
		if (!first.isObject() || first.properties().isEmpty()) {
			return;
		}
		String schemeName = first.properties().iterator().next().getKey();
		JsonNode schemes = root.path("components").path("securitySchemes").path(schemeName);
		String type = text(schemes.path("type"), "");
		if ("http".equals(type) && "bearer".equals(text(schemes.path("scheme"), ""))) {
			request.setAuthType("bearer");
		}
		else if ("http".equals(type) && "basic".equals(text(schemes.path("scheme"), ""))) {
			request.setAuthType("basic");
		}
		else if ("apiKey".equals(type)) {
			request.setAuthType("apikey");
			request.setApiKeyName(text(schemes.path("name"), "X-API-Key"));
			request.setApiKeyIn(text(schemes.path("in"), "header"));
		}
		else if ("oauth2".equals(type)) {
			request.setAuthType("oauth2");
		}
	}

	private ApiRequest findRequest(RequestCollection collection, String method, String path) {
		String upper = method.toUpperCase();
		for (ApiRequest request : collection.getRequests()) {
			String requestPath = MockServer.pathOf(request.getUrl());
			if (upper.equalsIgnoreCase(request.getMethod()) && path.equals(requestPath)) {
				return request;
			}
		}
		return null;
	}

	private JsonNode parse(String content) {
		try {
			if (content.trim().startsWith("{")) {
				return mapper.readTree(content);
			}
			Object loaded = new org.yaml.snakeyaml.Yaml().load(content);
			return mapper.valueToTree(loaded);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not parse OpenAPI document");
		}
	}

	private static String text(JsonNode node, String fallback) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return fallback;
		}
		String value = node.asText("").trim();
		return value.isBlank() ? fallback : value;
	}

	public record OpenApiDiff(List<String> added, List<String> updated, List<String> removed) {
	}

}
