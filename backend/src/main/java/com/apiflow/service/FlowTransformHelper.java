package com.apiflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class FlowTransformHelper {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Pattern JSON_PATH = Pattern.compile("\\$([\\w.\\[\\]]+)");

	private FlowTransformHelper() {
	}

	static String apply(String mode, String script, Map<String, String> context) throws Exception {
		if (script == null || script.isBlank()) {
			return "no-op";
		}
		String normalized = mode == null ? "fql" : mode.toLowerCase();
		if ("typescript".equals(normalized) || "ts".equals(normalized)) {
			return applyJs(stripTypes(script), context);
		}
		if ("js".equals(normalized) || "javascript".equals(normalized)) {
			return applyJs(script, context);
		}
		return applyFql(script, context);
	}

	private static String applyFql(String script, Map<String, String> context) {
		StringBuilder detail = new StringBuilder();
		for (String rawLine : script.split("\\R")) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
				continue;
			}
			String lower = line.toLowerCase();
			if (lower.startsWith("set ")) {
				int eq = line.indexOf('=');
				if (eq < 0) {
					continue;
				}
				String key = line.substring(4, eq).trim();
				String value = interpolate(line.substring(eq + 1).trim(), context);
				context.put(key, value);
				detail.append("set ").append(key).append("; ");
			}
			else if (lower.startsWith("delete ")) {
				String key = line.substring(7).trim();
				context.remove(key);
				detail.append("delete ").append(key).append("; ");
			}
			else if (lower.startsWith("copy ")) {
				String[] parts = line.substring(5).trim().split("\\s+", 2);
				if (parts.length == 2) {
					context.put(parts[1], context.getOrDefault(parts[0], ""));
					detail.append("copy ").append(parts[0]).append("->").append(parts[1]).append("; ");
				}
			}
			else if (lower.startsWith("pick ")) {
				String[] keys = line.substring(5).split(",");
				Map<String, String> picked = new LinkedHashMap<>();
				for (String key : keys) {
					String trimmed = key.trim();
					if (!trimmed.isEmpty()) {
						picked.put(trimmed, context.getOrDefault(trimmed, ""));
					}
				}
				context.clear();
				context.putAll(picked);
				detail.append("pick ").append(picked.size()).append(" keys; ");
			}
			else if (lower.startsWith("json ")) {
				String[] parts = line.substring(5).trim().split("\\s+", 2);
				if (parts.length == 2) {
					String source = context.getOrDefault(parts[0], "");
					String extracted = extractJsonPath(source, parts[1]);
					context.put(parts[1], extracted);
					detail.append("json ").append(parts[0]).append("->").append(parts[1]).append("; ");
				}
			}
			else {
				String key = "transform";
				context.put(key, interpolate(line, context));
				detail.append(line).append("; ");
			}
		}
		return detail.isEmpty() ? "fql ok" : detail.toString().trim();
	}

	private static String applyJs(String script, Map<String, String> context) {
		Map<String, Object> mutable = new LinkedHashMap<>(context);
		try (Context graal = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
			Value bindings = graal.getBindings("js");
			bindings.putMember("context", mutable);
			bindings.putMember("ctx", mutable);
			graal.eval("js", """
				const get = (key, fallback = '') => context[key] ?? fallback;
				const set = (key, value) => { context[key] = String(value ?? ''); };
				""");
			graal.eval("js", script);
		}
		context.clear();
		for (Map.Entry<String, Object> entry : mutable.entrySet()) {
			context.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
		}
		return "js ok";
	}

	private static String interpolate(String value, Map<String, String> context) {
		String result = value;
		for (Map.Entry<String, String> entry : context.entrySet()) {
			result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
		}
		Matcher matcher = JSON_PATH.matcher(result);
		StringBuffer buffer = new StringBuffer();
		while (matcher.find()) {
			String path = matcher.group(1);
			String sourceKey = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
			String extracted = extractJsonPath(context.getOrDefault(sourceKey, ""), path);
			matcher.appendReplacement(buffer, Matcher.quoteReplacement(extracted));
		}
		matcher.appendTail(buffer);
		return buffer.toString();
	}

	private static String extractJsonPath(String json, String path) {
		if (json == null || json.isBlank() || path == null || path.isBlank()) {
			return "";
		}
		try {
			JsonNode node = MAPPER.readTree(json);
			String[] parts = path.split("\\.");
			for (String part : parts) {
				if (node == null) {
					return "";
				}
				if (part.endsWith("]")) {
					int bracket = part.indexOf('[');
					String field = bracket >= 0 ? part.substring(0, bracket) : part;
					int index = Integer.parseInt(part.substring(bracket + 1, part.length() - 1));
					node = node.get(field).get(index);
				}
				else {
					node = node.get(part);
				}
			}
			if (node == null || node.isNull()) {
				return "";
			}
			return node.isValueNode() ? node.asText() : node.toString();
		}
		catch (Exception ex) {
			return "";
		}
	}

	static String stripTypes(String source) {
		String stripped = source.replaceAll("(?s)/\\*.*?\\*/", "");
		stripped = stripped.replaceAll("(?m)^\\s*(export\\s+)?interface\\s+[^{]+\\{[^}]*}\\s*", "");
		stripped = stripped.replaceAll("(?m)^\\s*(export\\s+)?type\\s+\\w+\\s*=\\s*[^;]+;", "");
		stripped = stripped.replaceAll("\\s+as\\s+[A-Za-z_][A-Za-z0-9_.<>,\\[\\]\\s|&]*", "");
		stripped = stripped.replaceAll("([,(]\\s*[A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*[A-Za-z_][A-Za-z0-9_.<>,\\[\\]\\s|&]*", "$1");
		stripped = stripped.replaceAll("(const|let|var)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*[A-Za-z_][A-Za-z0-9_.<>,\\[\\]\\s|&]*\\s*=", "$1 $2 =");
		return stripped;
	}

}
