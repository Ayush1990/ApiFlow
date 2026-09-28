package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

import com.apiflow.model.ChangeResult;
import com.apiflow.store.FileStore;

import tools.jackson.databind.ObjectMapper;

@Service
public class ScriptLibrary {

	private static final CopyOnWriteArrayList<PackageScript> PACKAGES = new CopyOnWriteArrayList<>();
	private final FileStore store;
	private final ObjectMapper mapper = new ObjectMapper();

	public ScriptLibrary(FileStore store) {
		this.store = store;
		reload();
	}

	public List<PackageScript> list() {
		return List.copyOf(PACKAGES);
	}

	public ChangeResult save(PackageScript script) {
		if (script.id == null || script.id.isBlank()) {
			script.id = UUID.randomUUID().toString();
		}
		if (script.name == null || script.name.isBlank()) {
			script.name = "package";
		}
		PACKAGES.removeIf(item -> item.id.equals(script.id));
		PACKAGES.add(script);
		persist();
		return new ChangeResult(store.copy(), script.id);
	}

	public static String source() {
		StringBuilder builder = new StringBuilder();
		builder.append("var __apiflowPackages = {};\nfunction require(name) { if (!Object.prototype.hasOwnProperty.call(__apiflowPackages, name)) { throw new Error('Package not found: ' + name); } return __apiflowPackages[name]; }\n");
		for (PackageScript script : PACKAGES) {
			if (script.code != null && !script.code.isBlank()) {
				String safeName = script.name == null ? "package" : script.name.replace("\\", "\\\\").replace("'", "\\'");
				builder.append("(function() {\nconst module = { exports: {} };\nconst exports = module.exports;\n")
					.append(script.code)
					.append("\n__apiflowPackages['").append(safeName).append("'] = module.exports;\n")
					.append("Object.keys(module.exports).forEach((key) => { globalThis[key] = module.exports[key]; });\n})();\n");
			}
		}
		return builder.toString();
	}

	private void reload() {
		try {
			var file = store.root().resolve("script-packages.json");
			if (!Files.exists(file)) {
				return;
			}
			PackageScript[] loaded = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8), PackageScript[].class);
			PACKAGES.clear();
			if (loaded != null) {
				PACKAGES.addAll(List.of(loaded));
			}
		}
		catch (Exception ignored) {
			PACKAGES.clear();
		}
	}

	private void persist() {
		try {
			var file = store.root().resolve("script-packages.json");
			Files.createDirectories(file.getParent());
			Files.writeString(file, mapper.writeValueAsString(new ArrayList<>(PACKAGES)), StandardCharsets.UTF_8);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not save package library");
		}
	}

	public static class PackageScript {
		public String id;
		public String name = "";
		public String code = "";
	}

}
