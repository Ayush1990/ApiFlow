package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ResponseQuery {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final JsonNode root;

	private ResponseQuery(JsonNode root) {
		this.root = root;
	}

	public static ResponseQuery parse(String body) {
		if (body == null || body.isBlank()) {
			return new ResponseQuery(null);
		}
		try {
			return new ResponseQuery(MAPPER.readTree(body));
		}
		catch (Exception ex) {
			return new ResponseQuery(null);
		}
	}

	@HostAccess.Export
	public Object get(String path) {
		if (root == null || path == null || path.isBlank()) {
			return null;
		}
		String trimmed = path.trim();
		if (trimmed.startsWith("$.")) {
			trimmed = trimmed.substring(2);
		}
		JsonNode node = root;
		for (String part : trimmed.split("\\.")) {
			if (node == null || part.isBlank()) {
				return null;
			}
			if (part.endsWith("]")) {
				int bracket = part.indexOf('[');
				String field = bracket >= 0 ? part.substring(0, bracket) : part;
				if (!field.isBlank()) {
					node = node.get(field);
				}
				String indexPart = part.substring(bracket + 1, part.length() - 1);
				if (node != null && node.isArray() && !indexPart.isBlank()) {
					try {
						node = node.get(Integer.parseInt(indexPart));
					}
					catch (NumberFormatException ex) {
						return null;
					}
				}
			}
			else {
				node = node.get(part);
			}
		}
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isTextual()) {
			return node.asString();
		}
		if (node.isNumber()) {
			return node.asDouble();
		}
		if (node.isBoolean()) {
			return node.asBoolean();
		}
		return node.toString();
	}

	@HostAccess.Export
	public List<Object> filter(String arrayPath, String field, Object expected) {
		List<Object> matches = new ArrayList<>();
		if (root == null) {
			return matches;
		}
		JsonNode array = nodeAt(arrayPath);
		if (array == null || !array.isArray()) {
			return matches;
		}
		for (JsonNode item : array) {
			JsonNode value = item.get(field);
			if (value != null && String.valueOf(expected).equals(value.asString())) {
				matches.add(item.toString());
			}
		}
		return matches;
	}

	private JsonNode nodeAt(String path) {
		Object value = get(path);
		if (value == null) {
			return null;
		}
		try {
			return MAPPER.readTree(String.valueOf(value));
		}
		catch (Exception ex) {
			return root.at("/" + path.replace("$.", "").replace('.', '/'));
		}
	}

}
