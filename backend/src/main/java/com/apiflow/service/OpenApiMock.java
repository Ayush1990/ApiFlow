package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class OpenApiMock {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private OpenApiMock() {
	}

	public static List<MockServer.Route> routes(String content) {
		List<MockServer.Route> routes = new ArrayList<>();
		try {
			JsonNode root = content.trim().startsWith("{")
				? MAPPER.readTree(content)
				: MAPPER.valueToTree(new org.yaml.snakeyaml.Yaml().load(content));
			JsonNode paths = root.get("paths");
			if (paths == null || !paths.isObject()) {
				return routes;
			}
			paths.properties().forEach(entry -> {
				String path = entry.getKey();
				JsonNode methods = entry.getValue();
				methods.properties().forEach(methodEntry -> {
					String method = methodEntry.getKey().toUpperCase(Locale.ROOT);
					if ("parameters".equals(method) || "summary".equals(method) || "description".equals(method)) {
						return;
					}
					JsonNode operation = methodEntry.getValue();
					String body = exampleBody(operation);
					routes.add(new MockServer.Route(method, path, "", 200, "application/json", body, "", 0, false));
				});
			});
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not parse OpenAPI spec for mock routes");
		}
		return routes;
	}

	private static String exampleBody(JsonNode operation) {
		JsonNode responses = operation.get("responses");
		if (responses == null) {
			return "{\"mock\":true}";
		}
		for (String code : List.of("200", "201", "202", "default")) {
			JsonNode response = responses.get(code);
			if (response == null) {
				continue;
			}
			JsonNode content = response.get("content");
			if (content == null) {
				return "{\"mock\":true}";
			}
			JsonNode json = content.get("application/json");
			if (json == null) {
				return "{\"mock\":true}";
			}
			JsonNode example = json.get("example");
			if (example != null) {
				return example.toString();
			}
			JsonNode schema = json.get("schema");
			if (schema != null && schema.get("example") != null) {
				return schema.get("example").toString();
			}
		}
		return "{\"mock\":true}";
	}

}
