package com.apiflow.service;

public final class BruYamlMigrator {

	private BruYamlMigrator() {
	}

	public static boolean looksLikeYamlV3(String content) {
		if (content == null || content.isBlank()) {
			return false;
		}
		String trimmed = content.trim();
		return trimmed.startsWith("---") || trimmed.startsWith("meta:") || trimmed.startsWith("type: http");
	}

	public static String toBru(String yaml) {
		if (yaml == null || yaml.isBlank()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		String section = "";
		for (String line : yaml.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			if (!line.startsWith(" ") && !line.startsWith("\t") && trimmed.endsWith(":") && !trimmed.contains(": ")) {
				section = trimmed.substring(0, trimmed.length() - 1).trim();
				if ("meta".equals(section)) {
					out.append("meta {\n");
				}
				else if ("headers".equals(section) || "params".equals(section) || "vars".equals(section)) {
					out.append(section).append(" {\n");
				}
				else if ("auth".equals(section)) {
					out.append("auth {\n");
				}
				else if ("body".equals(section)) {
					out.append("body:json {\n");
				}
				else if ("script".equals(section)) {
					out.append("script:pre-request {\n");
				}
				else if ("tests".equals(section)) {
					out.append("tests {\n");
				}
				continue;
			}
			int colon = trimmed.indexOf(':');
			if (colon <= 0) {
				if ("meta".equals(section) || "headers".equals(section) || "params".equals(section) || "vars".equals(section) || "auth".equals(section)) {
					out.append("}\n");
					section = "";
				}
				continue;
			}
			String key = trimmed.substring(0, colon).trim();
			String value = trimmed.substring(colon + 1).trim();
			if ("meta".equals(section)) {
				out.append("  ").append(key).append(": ").append(value).append('\n');
			}
			else if ("auth".equals(section) && "type".equals(key)) {
				out.append("  mode: ").append(value).append('\n');
			}
			else if ("body".equals(section)) {
				out.append("  ").append(value).append('\n');
			}
			else if ("script".equals(section) || "tests".equals(section)) {
				out.append(trimmed).append('\n');
			}
			else if ("headers".equals(section) || "params".equals(section) || "vars".equals(section)) {
				out.append("  ").append(key).append(": ").append(value).append('\n');
			}
			else if ("type".equals(key) && "http".equals(value)) {
				out.append("meta {\n  type: http\n");
				section = "meta";
			}
			else if ("method".equals(key) || "url".equals(key) || "name".equals(key)) {
				if (!out.toString().contains("meta {")) {
					out.append("meta {\n");
				}
				out.append("  ").append(key).append(": ").append(value).append('\n');
			}
		}
		if (out.toString().contains("{") && !out.toString().endsWith("}\n")) {
			out.append("}\n");
		}
		String bru = out.toString().trim();
		return bru.isBlank() ? yaml : bru;
	}

}
