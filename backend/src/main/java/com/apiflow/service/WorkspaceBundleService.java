package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.apiflow.model.Environment;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class WorkspaceBundleService {

	private final FileStore store;
	private final ExportService exportService;
	private final ObjectMapper mapper = new ObjectMapper();

	public WorkspaceBundleService(FileStore store, ExportService exportService) {
		this.store = store;
		this.exportService = exportService;
	}

	public String exportBundle() {
		Workspace workspace = store.copy();
		ObjectNode root = mapper.createObjectNode();
		root.put("format", "apiflow-workspace-bundle");
		root.put("version", 1);
		root.put("name", "ApiFlow Workspace");
		ArrayNode globalEnvs = root.putArray("globalEnvironments");
		ArrayNode collectionEnvs = root.putArray("environments");
		for (Environment environment : workspace.getEnvironments()) {
			ObjectNode node = mapper.createObjectNode();
			node.put("id", environment.getId());
			node.put("name", environment.getName());
			node.put("global", environment.isGlobal());
			node.put("collectionId", environment.getCollectionId());
			node.put("bru", exportService.exportEnvironment(environment));
			if (environment.isGlobal()) {
				globalEnvs.add(node);
			}
			else {
				collectionEnvs.add(node);
			}
		}
		ArrayNode collections = root.putArray("collections");
		for (RequestCollection collection : workspace.getCollections()) {
			ObjectNode node = mapper.createObjectNode();
			node.put("id", collection.getId());
			node.put("name", collection.getName());
			ArrayNode bruFolder = node.putArray("bruFolder");
			for (BruFolderExporter.BruFileEntry entry : BruFolderExporter.export(collection)) {
				ObjectNode file = mapper.createObjectNode();
				file.put("path", entry.path());
				file.put("content", entry.content());
				bruFolder.add(file);
			}
			collections.add(node);
		}
		root.set("settings", mapper.valueToTree(workspace.getSettings()));
		root.set("variables", mapper.valueToTree(workspace.getVariables()));
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not export workspace bundle");
		}
	}

	public Workspace importBundle(String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("Bundle content is required");
		}
		try {
			ObjectNode root = (ObjectNode) mapper.readTree(content);
			return store.update(workspace -> {
				if (root.has("settings")) {
					workspace.setSettings(mapper.treeToValue(root.get("settings"), workspace.getSettings().getClass()));
				}
				if (root.has("variables")) {
					workspace.setVariables(mapper.readerForListOf(com.apiflow.model.KeyValue.class).readValue(root.get("variables")));
				}
				List<Environment> environments = new ArrayList<>();
				importEnvironments(root.get("globalEnvironments"), environments, true);
				importEnvironments(root.get("environments"), environments, false);
				if (!environments.isEmpty()) {
					workspace.setEnvironments(environments);
				}
				if (root.has("collections")) {
					for (JsonNode node : root.get("collections")) {
						if (node.has("bruFolder")) {
							List<BrunoFolderImporter.BrunoFile> files = new ArrayList<>();
							for (JsonNode entry : node.get("bruFolder")) {
								files.add(new BrunoFolderImporter.BrunoFile(entry.get("path").asString(), entry.get("content").asString()));
							}
							RequestCollection imported = BrunoFolderImporter.importFiles(files);
							if (node.has("id")) {
								imported.setId(node.get("id").asString());
							}
							if (node.has("name")) {
								imported.setName(node.get("name").asString());
							}
							workspace.getCollections().add(imported);
						}
					}
				}
			});
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid workspace bundle: " + ex.getMessage());
		}
	}

	private void importEnvironments(JsonNode array, List<Environment> target, boolean globalDefault) {
		if (array == null || !array.isArray()) {
			return;
		}
		for (JsonNode node : array) {
			Environment environment = new Environment();
			environment.setId(node.path("id").asString(UUID.randomUUID().toString()));
			environment.setName(node.path("name").asString("Imported"));
			environment.setGlobal(node.path("global").asBoolean(globalDefault));
			environment.setCollectionId(node.path("collectionId").asString(""));
			if (node.has("bru")) {
				Environment parsed = BrunoFolderImporter.parseEnvironment(node.get("bru").asString());
				environment.setVariables(parsed.getVariables());
				environment.setExternalSecrets(parsed.getExternalSecrets());
			}
			target.add(environment);
		}
	}

}
