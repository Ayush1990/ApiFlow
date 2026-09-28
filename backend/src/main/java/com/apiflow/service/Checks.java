package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;

import com.apiflow.model.Assertion;
import com.apiflow.model.CheckResult;
import com.apiflow.model.KeyValue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class Checks {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private Checks() {
	}

	public static List<CheckResult> evaluate(List<Assertion> assertions, int status, String body, List<KeyValue> headers, long timeMs) {
		List<CheckResult> results = new ArrayList<>();
		if (assertions == null) {
			return results;
		}
		for (Assertion assertion : assertions) {
			if (assertion == null || !assertion.isEnabled()) {
				continue;
			}
			String type = assertion.getType() == null ? "status" : assertion.getType();
			String expected = assertion.getExpected() == null ? "" : assertion.getExpected();
			if ("contains".equals(type)) {
				boolean passed = body != null && body.contains(expected);
				results.add(new CheckResult(type, passed, passed ? "Body contains the expected text" : "Body does not contain the expected text"));
			}
			else if ("jsonEquals".equals(type)) {
				String actual = read(body, assertion.getPath());
				boolean passed = expected.equals(actual);
				results.add(new CheckResult(type, passed, passed ? assertion.getPath() + " matched" : assertion.getPath() + " was " + actual));
			}
			else if ("header".equals(type)) {
				String actual = header(headers, assertion.getPath());
				boolean passed = expected.equals(actual);
				results.add(new CheckResult(type, passed, passed ? assertion.getPath() + " matched" : assertion.getPath() + " was " + actual));
			}
			else if ("time".equals(type)) {
				long limit = parseLong(expected);
				boolean passed = limit >= 0 && timeMs <= limit;
				results.add(new CheckResult(type, passed, passed ? "Responded in " + timeMs + " ms" : "Took " + timeMs + " ms, limit " + expected));
			}
			else if ("regex".equals(type)) {
				boolean passed = matches(body, expected);
				results.add(new CheckResult(type, passed, passed ? "Body matched the pattern" : "Body did not match the pattern"));
			}
			else if ("exists".equals(type)) {
				String actual = read(body, assertion.getPath());
				boolean passed = actual != null && !actual.isBlank();
				results.add(new CheckResult(type, passed, passed ? assertion.getPath() + " exists" : assertion.getPath() + " missing"));
			}
			else if ("type".equals(type)) {
				String actual = read(body, assertion.getPath());
				boolean passed = matchesType(actual, expected);
				results.add(new CheckResult(type, passed, passed ? assertion.getPath() + " is " + expected : assertion.getPath() + " type mismatch"));
			}
			else if ("isJson".equals(type)) {
				boolean passed = isJson(body);
				results.add(new CheckResult(type, passed, passed ? "Body is valid JSON" : "Body is not valid JSON"));
			}
			else if ("isArray".equals(type)) {
				boolean passed = isJsonArray(body, assertion.getPath());
				results.add(new CheckResult(type, passed, passed ? "Value is an array" : "Value is not an array"));
			}
			else {
				boolean passed = String.valueOf(status).equals(expected.trim());
				results.add(new CheckResult("status", passed, passed ? "Status is " + status : "Expected status " + expected + " but got " + status));
			}
		}
		return results;
	}

	private static String header(List<KeyValue> headers, String name) {
		if (headers == null || name == null) {
			return "";
		}
		for (KeyValue header : headers) {
			if (header.getKey() != null && header.getKey().equalsIgnoreCase(name)) {
				return header.getValue() == null ? "" : header.getValue();
			}
		}
		return "";
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(value.trim());
		}
		catch (Exception ex) {
			return -1;
		}
	}

	private static boolean matches(String body, String pattern) {
		if (body == null || pattern == null || pattern.isBlank()) {
			return false;
		}
		try {
			return java.util.regex.Pattern.compile(pattern).matcher(body).find();
		}
		catch (Exception ex) {
			return false;
		}
	}

	private static boolean matchesType(String actual, String expected) {
		if (actual == null) {
			return "null".equalsIgnoreCase(expected);
		}
		return switch (expected == null ? "" : expected.trim().toLowerCase()) {
			case "string" -> !actual.startsWith("{") && !actual.startsWith("[") && !actual.equals("true") && !actual.equals("false") && !actual.matches("-?\\d+(\\.\\d+)?");
			case "number" -> actual.matches("-?\\d+(\\.\\d+)?");
			case "boolean" -> "true".equals(actual) || "false".equals(actual);
			case "object" -> actual.startsWith("{");
			case "array" -> actual.startsWith("[");
			default -> true;
		};
	}

	private static boolean isJson(String body) {
		if (body == null || body.isBlank()) {
			return false;
		}
		try {
			MAPPER.readTree(body);
			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}

	private static boolean isJsonArray(String body, String path) {
		try {
			JsonNode node = path == null || path.isBlank() ? MAPPER.readTree(body) : MAPPER.readTree(body).at("/" + path.replace('.', '/'));
			return node != null && node.isArray();
		}
		catch (Exception ex) {
			return false;
		}
	}

	public static String read(String json, String path) {
		if (json == null || json.isBlank() || path == null || path.isBlank()) {
			return null;
		}
		try {
			JsonNode node = MAPPER.readTree(json);
			for (String part : path.split("\\.")) {
				if (node == null || part.isBlank()) {
					return null;
				}
				if (part.matches("\\d+")) {
					node = node.get(Integer.parseInt(part));
				}
				else {
					node = node.get(part);
				}
			}
			if (node == null || node.isNull()) {
				return null;
			}
			return node.isValueNode() ? node.asString() : node.toString();
		}
		catch (Exception ex) {
			return null;
		}
	}

}
