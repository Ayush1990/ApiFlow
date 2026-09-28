package com.apiflow.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DotEnvLoader {

	private DotEnvLoader() {
	}

	public static Map<String, String> load(Path path) {
		Map<String, String> map = new LinkedHashMap<>();
		if (path == null || !Files.isRegularFile(path)) {
			return map;
		}
		try {
			for (String line : Files.readAllLines(path)) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}
				int equals = trimmed.indexOf('=');
				if (equals <= 0) {
					continue;
				}
				String key = trimmed.substring(0, equals).trim();
				String value = trimmed.substring(equals + 1).trim();
				if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
					value = value.substring(1, value.length() - 1);
				}
				map.put(key, value);
			}
		}
		catch (Exception ignored) {
			// Ignore unreadable dotenv files.
		}
		return map;
	}

}
