package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.apiflow.store.FileStore;

import tools.jackson.databind.ObjectMapper;

final class MockStateStore {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final ConcurrentHashMap<String, String> STATE = new ConcurrentHashMap<>();
	private static volatile FileStore files;

	private MockStateStore() {
	}

	static void bind(FileStore store) {
		files = store;
		load();
	}

	static String get(String key) {
		return STATE.getOrDefault(key, "{}");
	}

	static void put(String key, String json) {
		STATE.put(key, json == null || json.isBlank() ? "{}" : json);
		save();
	}

	private static void load() {
		Path file = path();
		if (file == null || !Files.exists(file)) {
			return;
		}
		try {
			Map<?, ?> parsed = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), Map.class);
			STATE.clear();
			for (Map.Entry<?, ?> entry : parsed.entrySet()) {
				STATE.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "{}" : String.valueOf(entry.getValue()));
			}
		}
		catch (Exception ignored) {
			STATE.clear();
		}
	}

	private static void save() {
		Path file = path();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, MAPPER.writeValueAsString(new LinkedHashMap<>(STATE)), StandardCharsets.UTF_8);
		}
		catch (Exception ignored) {
			// keep the in-memory copy if the disk write fails
		}
	}

	private static Path path() {
		return files == null ? null : files.root().resolve("mock-state.json");
	}

}
