package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.RunReport;
import com.apiflow.store.FileStore;

import tools.jackson.databind.ObjectMapper;

@Service
public class AppsService {

	private final FileStore store;
	private final WorkspaceService workspaceService;
	private final AiService aiService;
	private final ObjectMapper mapper = new ObjectMapper();

	public AppsService(FileStore store, WorkspaceService workspaceService, AiService aiService) {
		this.store = store;
		this.workspaceService = workspaceService;
		this.aiService = aiService;
	}

	public List<AppManifest> list() {
		List<AppManifest> apps = new ArrayList<>();
		Path dir = appsDir();
		if (!Files.isDirectory(dir)) {
			return apps;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
			for (Path file : files) {
				apps.add(mapper.readValue(file.toFile(), AppManifest.class));
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not read apps");
		}
		return apps;
	}

	public AppManifest save(AppManifest manifest) {
		if (manifest.getName() == null || manifest.getName().isBlank()) {
			throw new IllegalArgumentException("App name is required");
		}
		if (manifest.getId() == null || manifest.getId().isBlank()) {
			manifest.setId(UUID.randomUUID().toString());
		}
		try {
			Files.createDirectories(appsDir());
			mapper.writerWithDefaultPrettyPrinter().writeValue(appsDir().resolve(manifest.getId() + ".json").toFile(), manifest);
			return manifest;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not save app");
		}
	}

	public String generateHtml(String prompt, String context) {
		com.apiflow.model.Workspace workspace = store.copy();
		return aiService.suggestAppHtml(workspace.getSettings(), prompt, context);
	}

	public RunReport run(String appId, String environmentId) {
		AppManifest app = find(appId);
		if ("request".equals(app.getScope()) && app.getRequest() != null && !app.getRequest().isBlank()) {
			ExecuteResult result = runRequest(appId, app.getRequest(), environmentId);
			RunReport report = new RunReport();
			if (result.isOk()) {
				report.setPassed(1);
			}
			else {
				report.setFailed(1);
			}
			return report;
		}
		if ("collection".equals(app.getTriggerType())) {
			return workspaceService.runNamed(app.getCollection(), environmentId, app.getFolder(), false, "");
		}
		if (app.getScript() != null && !app.getScript().isBlank()) {
			ExecuteCommand command = new ExecuteCommand();
			command.setCollectionId(app.getCollection());
			ScriptRunner.run(app.getScript(), new java.util.LinkedHashMap<>(), command, store, app.getCollection());
		}
		RunReport report = new RunReport();
		report.setPassed(1);
		return report;
	}

	public ExecuteResult submitRequest(String appId, ExecuteCommand command, String environmentId) {
		AppManifest app = find(appId);
		if (command == null) {
			command = new ExecuteCommand();
		}
		if (command.getCollectionId() == null || command.getCollectionId().isBlank()) {
			command.setCollectionId(resolveCollectionId(app.getCollection()));
		}
		if (environmentId != null && !environmentId.isBlank()) {
			command.setEnvironmentId(environmentId);
		}
		return workspaceService.execute(command);
	}

	public void setVar(String appId, String name, String value, String environmentId, boolean envScope) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Variable name is required");
		}
		AppManifest app = find(appId);
		String collectionId = resolveCollectionId(app.getCollection());
		store.update(data -> {
			if (envScope && environmentId != null && !environmentId.isBlank()) {
				for (com.apiflow.model.Environment environment : data.getEnvironments()) {
					if (environmentId.equals(environment.getId())) {
						upsert(environment.getVariables(), name.trim(), value == null ? "" : value);
						return;
					}
				}
			}
			for (com.apiflow.model.RequestCollection collection : data.getCollections()) {
				if (collectionId.equals(collection.getId())) {
					upsert(collection.getVariables(), name.trim(), value == null ? "" : value);
					return;
				}
			}
			throw new IllegalArgumentException("Collection not found");
		});
	}

	private static void upsert(java.util.List<com.apiflow.model.KeyValue> variables, String key, String value) {
		for (com.apiflow.model.KeyValue variable : variables) {
			if (key.equals(variable.getKey())) {
				variable.setValue(value);
				variable.setEnabled(true);
				return;
			}
		}
		variables.add(new com.apiflow.model.KeyValue(key, value, true));
	}

	public ExecuteResult runRequest(String appId, String requestName, String environmentId) {
		AppManifest app = find(appId);
		String collectionId = resolveCollectionId(app.getCollection());
		com.apiflow.model.Workspace workspace = store.copy();
		com.apiflow.model.RequestCollection collection = workspace.getCollections().stream()
			.filter(item -> collectionId.equals(item.getId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Collection not found"));
		com.apiflow.model.ApiRequest request = collection.getRequests().stream()
			.filter(item -> requestName != null && (requestName.equals(item.getName()) || requestName.equals(item.getId())))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestName));
		return workspaceService.execute(WorkspaceService.commandFrom(request, collectionId, environmentId == null ? "" : environmentId));
	}

	private String resolveCollectionId(String collectionRef) {
		if (collectionRef == null || collectionRef.isBlank()) {
			throw new IllegalArgumentException("App collection is required");
		}
		return store.copy().getCollections().stream()
			.filter(item -> collectionRef.equals(item.getId()) || collectionRef.equalsIgnoreCase(item.getName()))
			.map(com.apiflow.model.RequestCollection::getId)
			.findFirst()
			.orElse(collectionRef);
	}

	public void delete(String appId) {
		try {
			Files.deleteIfExists(appsDir().resolve(appId + ".json"));
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not delete app");
		}
	}

	private AppManifest find(String appId) {
		Path file = appsDir().resolve(appId + ".json");
		if (!Files.exists(file)) {
			throw new IllegalArgumentException("App not found");
		}
		try {
			return mapper.readValue(file.toFile(), AppManifest.class);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not read app");
		}
	}

	private Path appsDir() {
		return store.root().resolve("apps");
	}

	public static class AppManifest {

		private String id;
		private String name;
		private String description = "";
		private String triggerType = "collection";
		private String collection = "";
		private String folder = "";
		private String script = "";
		private String icon = "⚡";
		private String htmlCode = "";
		private String uiType = "collection";
		private String scope = "workspace";
		private String request = "";

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getDescription() {
			return description == null ? "" : description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getTriggerType() {
			return triggerType == null ? "collection" : triggerType;
		}

		public void setTriggerType(String triggerType) {
			this.triggerType = triggerType;
		}

		public String getCollection() {
			return collection == null ? "" : collection;
		}

		public void setCollection(String collection) {
			this.collection = collection;
		}

		public String getFolder() {
			return folder == null ? "" : folder;
		}

		public void setFolder(String folder) {
			this.folder = folder;
		}

		public String getScript() {
			return script == null ? "" : script;
		}

		public void setScript(String script) {
			this.script = script;
		}

		public String getIcon() {
			return icon == null || icon.isBlank() ? "⚡" : icon;
		}

		public void setIcon(String icon) {
			this.icon = icon;
		}

		public String getHtmlCode() {
			return htmlCode == null ? "" : htmlCode;
		}

		public void setHtmlCode(String htmlCode) {
			this.htmlCode = htmlCode;
		}

		public String getUiType() {
			return uiType == null || uiType.isBlank() ? "collection" : uiType;
		}

		public void setUiType(String uiType) {
			this.uiType = uiType;
		}

		public String getScope() {
			return scope == null || scope.isBlank() ? "workspace" : scope;
		}

		public void setScope(String scope) {
			this.scope = scope;
		}

		public String getRequest() {
			return request == null ? "" : request;
		}

		public void setRequest(String request) {
			this.request = request;
		}

	}

}
