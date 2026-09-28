package com.apiflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class PostmanExportOrganizer {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private PostmanExportOrganizer() {
	}

	public static String organize(String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("Postman export is empty");
		}
		JsonNode root = parse(content);
		if (!root.has("item") || !root.has("info")) {
			throw new IllegalArgumentException("Not a Postman collection export");
		}
		ObjectNode cleaned = (ObjectNode) root.deepCopy();
		ArrayNode items = MAPPER.createArrayNode();
		dedupeItems(root.path("item"), items, "");
		cleaned.set("item", items);
		removeEmptyAuth(cleaned);
		return cleaned.toPrettyString();
	}

	public static Map<String, Object> summary(String content) {
		JsonNode root = parse(content);
		Map<String, Object> stats = new LinkedHashMap<>();
		int[] counts = { 0, 0, 0 };
		walkStats(root.path("item"), counts);
		stats.put("folders", counts[0]);
		stats.put("requests", counts[1]);
		stats.put("scripts", counts[2]);
		stats.put("name", text(root.path("info").path("name"), "Postman collection"));
		return stats;
	}

	private static JsonNode parse(String content) {
		try {
			return MAPPER.readTree(content.trim());
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not parse Postman JSON");
		}
	}

	private static void dedupeItems(JsonNode source, ArrayNode target, String prefix) {
		if (!source.isArray()) {
			return;
		}
		Map<String, JsonNode> seen = new LinkedHashMap<>();
		for (JsonNode item : source) {
			String name = text(item.path("name"), "Item");
			String key = prefix + "/" + name.toLowerCase();
			if (item.has("item")) {
				ObjectNode folder = (ObjectNode) item.deepCopy();
				ArrayNode children = MAPPER.createArrayNode();
				dedupeItems(item.path("item"), children, key);
				folder.set("item", children);
				target.add(folder);
			}
			else if (item.has("request")) {
				if (!seen.containsKey(key)) {
					seen.put(key, item);
					target.add(item.deepCopy());
				}
			}
		}
	}

	private static void walkStats(JsonNode items, int[] counts) {
		if (!items.isArray()) {
			return;
		}
		for (JsonNode item : items) {
			if (item.has("item")) {
				counts[0]++;
				walkStats(item.path("item"), counts);
			}
			else if (item.has("request")) {
				counts[1]++;
				if (item.path("event").isArray()) {
					for (JsonNode event : item.path("event")) {
						if (event.path("script").path("exec").isArray() && event.path("script").path("exec").size() > 0) {
							counts[2]++;
						}
					}
				}
			}
		}
	}

	private static void removeEmptyAuth(ObjectNode root) {
		if (root.path("auth").path("type").asString("").isBlank()) {
			root.remove("auth");
		}
	}

	private static String text(JsonNode node, String fallback) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return fallback;
		}
		String value = node.asString("");
		return value.isBlank() ? fallback : value;
	}

}
