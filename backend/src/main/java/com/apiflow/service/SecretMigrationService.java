package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.apiflow.model.Environment;
import com.apiflow.model.KeyValue;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SecretMigrationService {

	private final FileStore store;
	private final ObjectMapper mapper = new ObjectMapper();

	public SecretMigrationService(FileStore store) {
		this.store = store;
	}

	public Map<String, Object> preview() {
		Path secretsFile = store.root().resolve("secrets.json");
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("found", Files.exists(secretsFile));
		result.put("path", secretsFile.toString());
		if (!Files.exists(secretsFile)) {
			result.put("count", 0);
			return result;
		}
		try {
			JsonNode root = mapper.readTree(Files.readString(secretsFile, StandardCharsets.UTF_8));
			result.put("count", root.size());
			result.put("keys", root.propertyNames());
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not read secrets.json");
		}
		return result;
	}

	public Workspace migrate(String environmentId) {
		Path secretsFile = store.root().resolve("secrets.json");
		if (!Files.exists(secretsFile)) {
			throw new IllegalArgumentException("No secrets.json found in data root");
		}
		try {
			JsonNode root = mapper.readTree(Files.readString(secretsFile, StandardCharsets.UTF_8));
			return store.update(data -> {
				Environment target = data.getEnvironments().stream()
					.filter(env -> environmentId == null || environmentId.isBlank() || environmentId.equals(env.getId()))
					.findFirst()
					.orElseThrow(() -> new NotFoundException("Environment not found"));
				if (target.getExternalSecrets() == null) {
					target.setExternalSecrets(new LinkedHashMap<>());
				}
				root.properties().forEach(entry -> {
					String key = entry.getKey();
					String path = entry.getValue().asString("");
					target.getExternalSecrets().put(key, path);
					boolean exists = target.getVariables().stream().anyMatch(v -> key.equals(v.getKey()));
					if (!exists) {
						KeyValue variable = new KeyValue(key, "{{secret:" + path + "}}", true);
						variable.setSecret(true);
						target.getVariables().add(variable);
					}
				});
			});
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not migrate secrets.json");
		}
	}

}
