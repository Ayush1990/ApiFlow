package com.apiflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.ChangeResult;
import com.apiflow.model.Environment;
import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.Extractor;
import com.apiflow.model.Folder;
import com.apiflow.model.HistoryEntry;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.RunItem;
import com.apiflow.model.RunReport;
import com.apiflow.model.StoredCookie;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

@Service
public class WorkspaceService {

	private static final ThreadLocal<RunOptions> ACTIVE_RUN = new ThreadLocal<>();

	public static final class RunOptions {
		public boolean keepVariables = true;
		public boolean ignoreCookies;
		public boolean saveCookies = true;
		public String mockBaseUrl = "";
		public boolean shareResults;
		public java.util.List<String> requestIds;
		public boolean quietLogs;
	}
	private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "WS", "SSE", "MQTT", "SOCKETIO", "GRPC", "SOAP");
	private static final Set<String> BODY_TYPES = Set.of("none", "json", "text", "form", "multipart", "graphql", "xml", "soap");
	private static final Set<String> AUTH_TYPES = Set.of("none", "bearer", "basic", "apikey", "inherit", "oauth2", "digest", "aws", "ntlm", "oauth1", "edgegrid");

	private final FileStore store;
	private final RequestExecutor executor;
	private final ImportService imports;
	private final OAuthService oauth;
	private final SecretManagerService secrets;
	private final OpenApiSyncService openApiSync;
	private final OpenCollectionService openCollection;
	private final AiService ai;
	private final ScriptBridge scriptBridge;
	private final DatasetService datasetService;

	public WorkspaceService(FileStore store, RequestExecutor executor, ImportService imports, OAuthService oauth, SecretManagerService secrets, OpenApiSyncService openApiSync, OpenCollectionService openCollection, AiService ai, @Lazy ScriptBridge scriptBridge, DatasetService datasetService) {
		this.store = store;
		this.executor = executor;
		this.imports = imports;
		this.oauth = oauth;
		this.secrets = secrets;
		this.openApiSync = openApiSync;
		this.openCollection = openCollection;
		this.ai = ai;
		this.scriptBridge = scriptBridge;
		this.datasetService = datasetService;
	}

	public Workspace workspace() {
		return store.copy();
	}

	public ChangeResult updateWorkspace(Workspace incoming) {
		Workspace workspace = store.update(data -> {
			if (incoming.getVariables() != null) {
				data.setVariables(incoming.getVariables());
			}
			if (incoming.getSettings() != null) {
				data.setSettings(incoming.getSettings());
			}
		});
		return new ChangeResult(workspace, null);
	}

	public ChangeResult createCollection(String name) {
		String id = UUID.randomUUID().toString();
		Workspace workspace = store.update(data -> {
			RequestCollection collection = new RequestCollection();
			collection.setId(id);
			collection.setName(cleanName(name, "New Collection"));
			data.getCollections().add(collection);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult renameCollection(String id, String name) {
		String collectionName = requireName(name);
		Workspace workspace = store.update(data -> findCollection(data, id).setName(collectionName));
		return new ChangeResult(workspace, id);
	}

	public ChangeResult updateCollection(String id, RequestCollection incoming) {
		Workspace workspace = store.update(data -> {
			RequestCollection collection = findCollection(data, id);
			if (incoming != null && incoming.getName() != null && !incoming.getName().isBlank()) {
				collection.setName(cleanName(incoming.getName(), collection.getName()));
			}
			if (incoming != null && incoming.getVariables() != null) {
				collection.setVariables(incoming.getVariables());
			}
			if (incoming != null && incoming.getDefaults() != null) {
				collection.setDefaults(incoming.getDefaults());
			}
			if (incoming != null && incoming.getDocs() != null) {
				collection.setDocs(incoming.getDocs());
			}
			if (incoming != null && incoming.getPreRequestScript() != null) {
				collection.setPreRequestScript(incoming.getPreRequestScript());
			}
			if (incoming != null && incoming.getPostResponseScript() != null) {
				collection.setPostResponseScript(incoming.getPostResponseScript());
			}
			if (incoming != null && incoming.getTypedParams() != null) {
				collection.setTypedParams(incoming.getTypedParams());
			}
			if (incoming != null && incoming.getTypedHeaders() != null) {
				collection.setTypedHeaders(incoming.getTypedHeaders());
			}
			if (incoming != null && incoming.getBodySchema() != null) {
				collection.setBodySchema(incoming.getBodySchema());
			}
			if (incoming != null && incoming.getMockScenarios() != null) {
				collection.setMockScenarios(incoming.getMockScenarios());
			}
			if (incoming != null && incoming.getMockScript() != null) {
				collection.setMockScript(incoming.getMockScript());
			}
			if (incoming != null) {
				collection.setDocsPublic(incoming.isDocsPublic());
			}
			if (incoming != null && incoming.getPublishedDocsUrl() != null) {
				collection.setPublishedDocsUrl(incoming.getPublishedDocsUrl());
			}
			if (incoming != null && incoming.getSpecId() != null) {
				collection.setSpecId(incoming.getSpecId());
			}
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult updateCollectionSpecLink(String id, String specId) {
		Workspace workspace = store.update(data -> findCollection(data, id).setSpecId(specId == null ? "" : specId));
		return new ChangeResult(workspace, id);
	}

	public ChangeResult saveDocument(com.apiflow.model.WorkspaceDocument document) {
		if (document.getId() == null || document.getId().isBlank()) {
			document.setId(UUID.randomUUID().toString());
		}
		document.setUpdatedAt(System.currentTimeMillis());
		String id = document.getId();
		Workspace workspace = store.update(data -> {
			data.getDocuments().removeIf(item -> item.getId().equals(id));
			data.getDocuments().add(document);
		});
		ActivityLog.add(store, "document", "Updated document " + document.getTitle());
		return new ChangeResult(workspace, id);
	}

	public ChangeResult deleteDocument(String id) {
		Workspace workspace = store.update(data -> data.getDocuments().removeIf(item -> item.getId().equals(id)));
		return new ChangeResult(workspace, "");
	}

	public ChangeResult updateCollectionDocs(String id, String docs) {
		Workspace workspace = store.update(data -> findCollection(data, id).setDocs(docs == null ? "" : docs));
		return new ChangeResult(workspace, id);
	}

	public ChangeResult deleteCollection(String id) {
		Workspace workspace = store.update(data -> {
			boolean removed = data.getCollections().removeIf(collection -> id.equals(collection.getId()));
			if (!removed) {
				throw new NotFoundException("Collection not found");
			}
		});
		return new ChangeResult(workspace, null);
	}

	public RequestCollection exportCollection(String id) {
		return findCollection(store.copy(), id);
	}

	public ChangeResult importDocument(String content) {
		RequestCollection imported = imports.importDocument(content);
		Workspace workspace = store.update(data -> data.getCollections().add(imported));
		return new ChangeResult(workspace, imported.getId());
	}

	public ChangeResult importBruno(List<BrunoFolderImporter.BrunoFile> files) {
		List<Environment> environments = BrunoFolderImporter.environments(files);
		RequestCollection imported;
		try {
			imported = BrunoFolderImporter.importFiles(files);
		}
		catch (IllegalArgumentException ex) {
			if (environments.isEmpty()) {
				throw ex;
			}
			imported = null;
		}
		RequestCollection collection = imported;
		Workspace workspace = store.update(data -> {
			if (collection != null) {
				data.getCollections().add(collection);
			}
			data.getEnvironments().addAll(environments);
		});
		return new ChangeResult(workspace, collection != null ? collection.getId() : environments.get(0).getId());
	}

	public RunReport runNamed(String collectionName, String environmentName, String folderName, boolean stopOnFailure, String dataCsv) {
		return runNamed(collectionName, environmentName, folderName, stopOnFailure, dataCsv, false, 0);
	}

	public RunReport runNamed(String collectionName, String environmentName, String folderName, boolean stopOnFailure, String dataCsv, boolean parallel, int delayMs) {
		Workspace snapshot = store.copy();
		RequestCollection collection = snapshot.getCollections().stream()
			.filter(item -> collectionName != null && (collectionName.equals(item.getId()) || collectionName.equalsIgnoreCase(item.getName())))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Collection not found"));
		String environmentId = "";
		if (environmentName != null && !environmentName.isBlank()) {
			environmentId = snapshot.getEnvironments().stream()
				.filter(item -> environmentName.equals(item.getId()) || environmentName.equalsIgnoreCase(item.getName()))
				.map(Environment::getId)
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Environment not found"));
		}
		String folderId = "";
		if (folderName != null && !folderName.isBlank()) {
			folderId = collection.getFolders().stream()
				.filter(item -> folderName.equals(item.getId()) || folderName.equalsIgnoreCase(item.getName()))
				.map(Folder::getId)
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Folder not found"));
		}
		return runCollection(collection.getId(), environmentId, folderId, stopOnFailure, dataCsv, parallel, delayMs);
	}

	public ChangeResult createFolder(String collectionId, String name, String parentId) {
		String id = UUID.randomUUID().toString();
		Workspace workspace = store.update(data -> {
			RequestCollection collection = findCollection(data, collectionId);
			Folder folder = new Folder();
			folder.setId(id);
			folder.setName(cleanName(name, "New Folder"));
			folder.setParentId(parentId == null ? "" : parentId);
			folder.setPosition(collection.getFolders().size());
			collection.getFolders().add(folder);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult renameFolder(String id, String name) {
		String folderName = requireName(name);
		Workspace workspace = store.update(data -> {
			for (RequestCollection collection : data.getCollections()) {
				for (Folder folder : collection.getFolders()) {
					if (id.equals(folder.getId())) {
						folder.setName(folderName);
						return;
					}
				}
			}
			throw new NotFoundException("Folder not found");
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult updateFolder(String id, Folder incoming) {
		Workspace workspace = store.update(data -> {
			for (RequestCollection collection : data.getCollections()) {
				for (Folder folder : collection.getFolders()) {
					if (id.equals(folder.getId())) {
						if (incoming != null && incoming.getName() != null && !incoming.getName().isBlank()) {
							folder.setName(cleanName(incoming.getName(), folder.getName()));
						}
						if (incoming != null && incoming.getDefaults() != null) {
							folder.setDefaults(incoming.getDefaults());
						}
						if (incoming != null && incoming.getVariables() != null) {
							folder.setVariables(incoming.getVariables());
						}
						if (incoming != null && incoming.getDocs() != null) {
							folder.setDocs(incoming.getDocs());
						}
						if (incoming != null && incoming.getPreRequestScript() != null) {
							folder.setPreRequestScript(incoming.getPreRequestScript());
						}
						if (incoming != null && incoming.getPostResponseScript() != null) {
							folder.setPostResponseScript(incoming.getPostResponseScript());
						}
						return;
					}
				}
			}
			throw new NotFoundException("Folder not found");
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult deleteFolder(String id) {
		Workspace workspace = store.update(data -> {
			for (RequestCollection collection : data.getCollections()) {
				Folder folder = collection.getFolders().stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null);
				if (folder == null) {
					continue;
				}
				String parent = folder.getParentId();
				collection.getFolders().removeIf(item -> id.equals(item.getId()) || isDescendant(collection, item.getId(), id));
				for (ApiRequest request : collection.getRequests()) {
					if (id.equals(request.getFolderId()) || isDescendant(collection, request.getFolderId(), id)) {
						request.setFolderId(parent);
					}
				}
				return;
			}
			throw new NotFoundException("Folder not found");
		});
		return new ChangeResult(workspace, null);
	}

	public ChangeResult createRequest(String collectionId, String name, String folderId) {
		String id = UUID.randomUUID().toString();
		Workspace workspace = store.update(data -> {
			RequestCollection collection = findCollection(data, collectionId);
			ApiRequest request = new ApiRequest();
			request.setId(id);
			request.setName(cleanName(name, "New Request"));
			request.setMethod("GET");
			request.setUrl("");
			request.setBodyType("none");
			request.setAuthType("inherit");
			request.setFolderId(folderId == null ? "" : folderId);
			request.setPosition(collection.getRequests().size());
			collection.getRequests().add(request);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult updateRequest(String id, ApiRequest incoming) {
		Workspace workspace = store.update(data -> copyRequest(findRequest(data, id), incoming));
		return new ChangeResult(workspace, id);
	}

	public ChangeResult createAdhocRequest() {
		String id = "adhoc-" + UUID.randomUUID();
		Workspace workspace = store.update(data -> {
			ApiRequest request = new ApiRequest();
			request.setId(id);
			request.setName("Untitled");
			request.setMethod("GET");
			request.setUrl("");
			request.setBodyType("none");
			request.setAuthType("none");
			data.getAdhocRequests().add(request);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult updateAdhocRequest(String id, ApiRequest incoming) {
		Workspace workspace = store.update(data -> {
			ApiRequest request = findAdhocRequest(data, id);
			copyRequest(request, incoming);
			request.setId(id);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult deleteAdhocRequest(String id) {
		Workspace workspace = store.update(data -> {
			if (!data.getAdhocRequests().removeIf(request -> id.equals(request.getId()))) {
				throw new NotFoundException("Adhoc request not found");
			}
		});
		return new ChangeResult(workspace, null);
	}

	public ChangeResult saveAdhocToCollection(String adhocId, String collectionId, String folderId) {
		String newId = UUID.randomUUID().toString();
		Workspace workspace = store.update(data -> {
			ApiRequest adhoc = findAdhocRequest(data, adhocId);
			RequestCollection collection = findCollection(data, collectionId);
			ApiRequest request = new ApiRequest();
			copyRequest(request, adhoc);
			request.setId(newId);
			request.setFolderId(folderId == null ? "" : folderId);
			request.setPosition(collection.getRequests().size());
			collection.getRequests().add(request);
			data.getAdhocRequests().removeIf(item -> adhocId.equals(item.getId()));
		});
		return new ChangeResult(workspace, newId);
	}

	private static ApiRequest findAdhocRequest(Workspace data, String id) {
		return data.getAdhocRequests().stream()
			.filter(request -> id.equals(request.getId()))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Adhoc request not found"));
	}

	public ChangeResult deleteRequest(String id) {
		Workspace workspace = store.update(data -> {
			boolean removed = false;
			for (RequestCollection collection : data.getCollections()) {
				if (collection.getRequests().removeIf(request -> id.equals(request.getId()))) {
					removed = true;
				}
			}
			if (!removed) {
				throw new NotFoundException("Request not found");
			}
		});
		return new ChangeResult(workspace, null);
	}

	public ChangeResult duplicateRequest(String id) {
		String copyId = UUID.randomUUID().toString();
		Workspace workspace = store.update(data -> {
			ApiRequest source = findRequest(data, id);
			ApiRequest copy = new ApiRequest();
			copyRequest(copy, source);
			copy.setId(copyId);
			copy.setName(cleanName(source.getName() + " copy", "Request copy"));
			copy.setPosition(source.getPosition() + 1);
			findCollectionOf(data, id).getRequests().add(copy);
		});
		return new ChangeResult(workspace, copyId);
	}

	public ChangeResult reorder(String collectionId, String folderId, List<String> requestIds) {
		Workspace workspace = store.update(data -> {
			RequestCollection collection = findCollection(data, collectionId);
			String target = folderId == null ? "" : folderId;
			int index = 0;
			for (String requestId : requestIds) {
				ApiRequest request = findRequest(data, requestId);
				request.setFolderId(target);
				request.setPosition(index++);
			}
			collection.getRequests().sort(Comparator.comparingInt(ApiRequest::getPosition));
		});
		return new ChangeResult(workspace, collectionId);
	}

	public ChangeResult createEnvironment(String name) {
		String id = UUID.randomUUID().toString();
		Workspace workspace = store.update(data -> {
			Environment environment = new Environment();
			environment.setId(id);
			environment.setName(cleanName(name, "New Environment"));
			environment.setVariables(new ArrayList<>());
			data.getEnvironments().add(environment);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult updateEnvironment(String id, Environment incoming) {
		Workspace workspace = store.update(data -> {
			Environment environment = findEnvironment(data, id);
			environment.setName(cleanName(incoming.getName(), "Untitled Environment"));
			environment.setVariables(incoming.getVariables());
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult deleteEnvironment(String id) {
		Workspace workspace = store.update(data -> {
			boolean removed = data.getEnvironments().removeIf(environment -> id.equals(environment.getId()));
			if (!removed) {
				throw new NotFoundException("Environment not found");
			}
		});
		return new ChangeResult(workspace, null);
	}

	public ExecuteResult execute(ExecuteCommand command) {
		return execute(command, Map.of());
	}

	public ExecuteResult execute(ExecuteCommand command, Map<String, String> extra) {
		Map<String, String> merged = new LinkedHashMap<>();
		if (command.getPromptVars() != null) {
			merged.putAll(command.getPromptVars());
		}
		if (extra != null) {
			merged.putAll(extra);
		}
		extra = merged;
		Workspace snapshot = store.copy();
		if (snapshot.getSettings() != null) {
			if ((command.getExtras().getProxyUrl() == null || command.getExtras().getProxyUrl().isBlank())
					&& !snapshot.getSettings().getProxyUrl().isBlank()) {
				command.getExtras().setProxyUrl(snapshot.getSettings().getProxyUrl());
			}
			merged.putAll(DotEnvLoader.load(store.root().resolve(snapshot.getSettings().getDotenvPath())));
		}
		extra = merged;
		Environment environment = environment(snapshot, command.getEnvironmentId());
		RequestCollection collection = collectionOrNull(snapshot, command.getCollectionId());
		if (collection != null && (command.getFolderId() == null || command.getFolderId().isBlank())) {
			for (ApiRequest request : collection.getRequests()) {
				if (request.getId() != null && request.getId().equals(command.getRequestId())) {
					command.setFolderId(request.getFolderId());
				}
			}
		}
		Inheritance.apply(collection, command);
		ApiRequest request = requestOrNull(snapshot, command.getRequestId());
		Map<String, String> variables = VariableResolver.resolve(snapshot, collection, request, environment, extra, secrets, command.getGlobalEnvironmentId());
		Map<String, String> before = new LinkedHashMap<>(variables);
		if (snapshot.getSettings() != null) {
			SecretRefs.applyCommand(command, secrets, snapshot.getSettings().getSecretManager());
		}
		try {
			oauth.apply(command, variables);
		}
		catch (IllegalArgumentException ex) {
			return ExecuteResult.failure(ex.getMessage());
		}
		List<StoredCookie> cookies = ACTIVE_RUN.get() != null && ACTIVE_RUN.get().ignoreCookies
			? new ArrayList<>()
			: new ArrayList<>(snapshot.getCookies());
		scriptBridge.bindCookies(cookies);
		String collectionId = command.getCollectionId() == null ? "" : command.getCollectionId();
		String scriptMode = snapshot.getSettings() == null ? "safe" : snapshot.getSettings().getScriptMode();
		List<String> scriptLogs = new ArrayList<>();
		List<com.apiflow.model.TimelineEntry> scriptTimeline = new ArrayList<>();
		ScriptScope.attachLogSink(scriptLogs);
		ScriptScope.attachTimelineSink(scriptTimeline);
		try {
			if (collection != null && collection.getPreRequestScript() != null && !collection.getPreRequestScript().isBlank()) {
				ScriptRunner.run(collection.getPreRequestScript(), variables, command, store, collectionId, scriptMode);
			}
			if (collection != null) {
				for (Folder folder : VariableResolver.folderChain(collection, command.getFolderId())) {
					if (folder.getPreRequestScript() != null && !folder.getPreRequestScript().isBlank()) {
						ScriptRunner.run(folder.getPreRequestScript(), variables, command, store, collectionId, scriptMode);
					}
				}
			}
			ExecuteResult result = executor.execute(command, variables, cookies);
			if (collection != null) {
				for (int index = VariableResolver.folderChain(collection, command.getFolderId()).size() - 1; index >= 0; index--) {
					Folder folder = VariableResolver.folderChain(collection, command.getFolderId()).get(index);
					if (folder.getPostResponseScript() != null && !folder.getPostResponseScript().isBlank()) {
						try {
							ScriptRunner.runResponse(folder.getPostResponseScript(), variables, result, store, collectionId, scriptMode);
						}
						catch (IllegalArgumentException ex) {
							result.getChecks().add(new com.apiflow.model.CheckResult("folder-script", false, ex.getMessage()));
						}
					}
				}
				if (collection.getPostResponseScript() != null && !collection.getPostResponseScript().isBlank()) {
					try {
						ScriptRunner.runResponse(collection.getPostResponseScript(), variables, result, store, collectionId, scriptMode);
					}
					catch (IllegalArgumentException ex) {
						result.getChecks().add(new com.apiflow.model.CheckResult("collection-script", false, ex.getMessage()));
					}
				}
			}
			store.update(data -> {
				RunOptions options = ACTIVE_RUN.get();
				if (options == null || options.saveCookies) {
					remember(data, command, result, cookies, request);
				}
				else {
					remember(data, command, result, data.getCookies(), request);
				}
				if (options == null || options.keepVariables) {
					persistVars(data, command, before, variables);
				}
			});
			result.setScriptLogs(scriptLogs);
			RunOptions runOptions = ACTIVE_RUN.get();
			if (runOptions != null && runOptions.quietLogs) {
				result.setScriptLogs(java.util.List.of());
			}
			for (com.apiflow.model.TimelineEntry entry : scriptTimeline) {
				result.getTimeline().add(entry);
			}
			for (String line : scriptLogs) {
				result.getTimeline().add(new com.apiflow.model.TimelineEntry("console", "Script console", System.currentTimeMillis(), line));
			}
			result.getTimeline().sort(java.util.Comparator.comparingLong(com.apiflow.model.TimelineEntry::getAtMs));
			return result;
		}
		finally {
			ScriptScope.detachLogSink();
			ScriptScope.detachTimelineSink();
		}
	}

	public RunReport runCollection(String collectionId, String environmentId, String folderId, boolean stopOnFailure, String dataCsv) {
		return runCollection(collectionId, environmentId, folderId, stopOnFailure, dataCsv, false, 0);
	}

	public RunReport runCollection(String collectionId, String environmentId, String folderId, boolean stopOnFailure, String dataCsv, boolean parallel, int delayMs) {
		return runCollection(collectionId, environmentId, folderId, stopOnFailure, dataCsv, parallel, delayMs, List.of());
	}

	public RunReport runCollection(String collectionId, String environmentId, String folderId, boolean stopOnFailure, String dataCsv, boolean parallel, int delayMs, List<String> tags) {
		return runCollection(collectionId, environmentId, "", folderId, stopOnFailure, dataCsv, parallel, delayMs, tags);
	}

	public RunReport runCollection(String collectionId, String environmentId, String globalEnvironmentId, String folderId, boolean stopOnFailure, String dataCsv, boolean parallel, int delayMs, List<String> tags) {
		return runCollection(collectionId, environmentId, globalEnvironmentId, folderId, stopOnFailure, dataCsv, parallel, delayMs, tags, "");
	}

	public RunReport runCollection(String collectionId, String environmentId, String globalEnvironmentId, String folderId, boolean stopOnFailure, String dataCsv, boolean parallel, int delayMs, List<String> tags, String datasetId) {
		return runCollection(collectionId, environmentId, globalEnvironmentId, folderId, stopOnFailure, dataCsv, parallel, delayMs, tags, datasetId, new RunOptions());
	}

	public RunReport runCollection(String collectionId, String environmentId, String globalEnvironmentId, String folderId, boolean stopOnFailure, String dataCsv, boolean parallel, int delayMs, List<String> tags, String datasetId, RunOptions options) {
		RunOptions active = options == null ? new RunOptions() : options;
		long startedAt = System.currentTimeMillis();
		ACTIVE_RUN.set(active);
		try {
			return finishRun(collectionId, environmentId, globalEnvironmentId, folderId, stopOnFailure, dataCsv, parallel, delayMs, tags, datasetId, active, startedAt);
		}
		finally {
			ACTIVE_RUN.remove();
		}
	}

	private RunReport finishRun(String collectionId, String environmentId, String globalEnvironmentId, String folderId, boolean stopOnFailure, String dataCsv, boolean parallel, int delayMs, List<String> tags, String datasetId, RunOptions options, long startedAt) {
		Workspace snapshot = store.copy();
		RequestCollection collection = findCollection(snapshot, collectionId);
		Environment environment = environment(snapshot, environmentId);
		Environment globalEnvironment = environment(snapshot, globalEnvironmentId);
		List<String> secrets = SecretMasker.collectSecrets(
			snapshot.getVariables(),
			collection.getVariables(),
			environment == null ? List.of() : environment.getVariables(),
			globalEnvironment == null ? List.of() : globalEnvironment.getVariables());
		List<ApiRequest> requests = new ArrayList<>();
		for (ApiRequest request : collection.getRequests()) {
			if (folderId == null || folderId.isBlank() || isDescendant(collection, request.getFolderId(), folderId)) {
				if (matchesTags(request, tags)) {
					requests.add(request);
				}
			}
		}
		requests.sort(Comparator.comparingInt(ApiRequest::getPosition));
		if (options.requestIds != null) {
			Map<String, Integer> order = new LinkedHashMap<>();
			for (int index = 0; index < options.requestIds.size(); index++) {
				order.put(options.requestIds.get(index), index);
			}
			requests.removeIf(request -> !order.containsKey(request.getId()));
			requests.sort(Comparator.comparingInt(request -> order.getOrDefault(request.getId(), 0)));
		}
		List<Map<String, String>> rows = new ArrayList<>();
		if (datasetId != null && !datasetId.isBlank()) {
			rows.addAll(datasetService.rowsAsMaps(datasetId, null));
		}
		if (rows.isEmpty()) {
			rows.addAll(CsvTable.parse(dataCsv));
		}
		if (rows.isEmpty()) {
			rows.add(Map.of());
		}
		RunReport report = new RunReport();
		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			String label = rows.size() == 1 ? "" : "row " + (rowIndex + 1);
			Map<String, String> row = rows.get(rowIndex);
			try {
				RunnerContext.begin(rowIndex, rows.size(), row);
				if (parallel && requests.size() > 1) {
					java.util.List<RunItem> batch = java.util.Collections.synchronizedList(new ArrayList<>());
					requests.parallelStream().forEach(request -> {
						ExecuteResult result = execute(rewriteMock(commandFrom(request, collectionId, environmentId, globalEnvironmentId), options.mockBaseUrl), row);
						batch.add(runItem(request, result, label, secrets));
					});
					for (RunItem item : batch) {
						count(report, item, stopOnFailure);
						if (report.isStopped()) {
							return persistRun(report, collectionId, startedAt, options, snapshot, collection, environment, globalEnvironment);
						}
					}
				}
				else {
					int index = 0;
					while (index < requests.size()) {
						ApiRequest request = requests.get(index);
						RunnerContext.State state = RunnerContext.current();
						if (state != null) {
							state.setSkipCurrent(false);
							state.setNextRequest("");
						}
						ExecuteResult result = execute(rewriteMock(commandFrom(request, collectionId, environmentId, globalEnvironmentId), options.mockBaseUrl), row);
						RunItem item = runItem(request, result, label, secrets);
						if (result.getStatus() == 0 && "Skipped".equals(result.getStatusText())) {
							item.setOk(true);
							item.setError("skipped");
						}
						count(report, item, stopOnFailure);
						if (report.isStopped()) {
							return persistRun(report, collectionId, startedAt, options, snapshot, collection, environment, globalEnvironment);
						}
						state = RunnerContext.current();
						if (state != null && state.isStopRun()) {
							report.setStopped(true);
							return persistRun(report, collectionId, startedAt, options, snapshot, collection, environment, globalEnvironment);
						}
						if (state != null && !state.getNextRequest().isBlank()) {
							int jump = findRequestIndex(requests, state.getNextRequest());
							index = jump >= 0 ? jump : index + 1;
						}
						else {
							index++;
						}
						if (delayMs > 0) {
							try {
								Thread.sleep(delayMs);
							}
							catch (InterruptedException ex) {
								Thread.currentThread().interrupt();
								report.setStopped(true);
								return persistRun(report, collectionId, startedAt, options, snapshot, collection, environment, globalEnvironment);
							}
						}
					}
				}
			}
			finally {
				RunnerContext.clear();
			}
		}
		return persistRun(report, collectionId, startedAt, options, snapshot, collection, environment, globalEnvironment);
	}

	private RunReport persistRun(RunReport report, String collectionId, long startedAt, RunOptions options, Workspace snapshot, RequestCollection collection, Environment environment, Environment globalEnvironment) {
		if (report.getId() == null || report.getId().isBlank()) {
			report.setId(UUID.randomUUID().toString());
			report.setCollectionId(collectionId);
			report.setStartedAt(startedAt);
			if (options.shareResults) {
				String html = ReportGenerator.html(report, collection.getName(), secrets(snapshot, collection, environment, globalEnvironment));
				try {
					java.nio.file.Path dir = store.root().resolve("public");
					java.nio.file.Files.createDirectories(dir);
					java.nio.file.Path file = dir.resolve("run-" + report.getId() + ".html");
					java.nio.file.Files.writeString(file, html);
					report.setShareFile(file.toString());
					report.setShareUrl("/public/" + file.getFileName());
				}
				catch (java.io.IOException ex) {
					report.setShareUrl("");
				}
			}
			store.update(data -> {
				data.getCollectionRuns().add(0, report);
				while (data.getCollectionRuns().size() > 40) {
					data.getCollectionRuns().remove(data.getCollectionRuns().size() - 1);
				}
			});
		}
		return report;
	}

	private static ExecuteCommand rewriteMock(ExecuteCommand command, String mockBaseUrl) {
		if (mockBaseUrl == null || mockBaseUrl.isBlank() || command.getUrl() == null) {
			return command;
		}
		String base = mockBaseUrl.endsWith("/") ? mockBaseUrl.substring(0, mockBaseUrl.length() - 1) : mockBaseUrl;
		try {
			java.net.URI uri = java.net.URI.create(command.getUrl());
			if (uri.getHost() != null) {
				String path = uri.getRawPath() == null ? "" : uri.getRawPath();
				String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
				command.setUrl(base + path + query);
				return command;
			}
		}
		catch (IllegalArgumentException ignored) {
			// fall through
		}
		String path = command.getUrl().startsWith("/") ? command.getUrl() : "/" + command.getUrl();
		command.setUrl(base + path);
		return command;
	}

	private static List<String> secrets(Workspace snapshot, RequestCollection collection, Environment environment, Environment globalEnvironment) {
		return SecretMasker.collectSecrets(
			snapshot.getVariables(),
			collection.getVariables(),
			environment == null ? List.of() : environment.getVariables(),
			globalEnvironment == null ? List.of() : globalEnvironment.getVariables());
	}

	private static boolean matchesTags(ApiRequest request, List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return true;
		}
		if (request.getTags() == null || request.getTags().isEmpty()) {
			return false;
		}
		for (String tag : tags) {
			if (tag != null && !tag.isBlank() && TagExpression.matches(request.getTags(), tag)) {
				return true;
			}
		}
		return false;
	}

	private static int findRequestIndex(List<ApiRequest> requests, String name) {
		for (int index = 0; index < requests.size(); index++) {
			ApiRequest request = requests.get(index);
			if (name.equals(request.getName()) || name.equals(request.getId())) {
				return index;
			}
		}
		return -1;
	}

	private static void count(RunReport report, RunItem item, boolean stopOnFailure) {
		if (item.isOk()) {
			report.setPassed(report.getPassed() + 1);
		}
		else {
			report.setFailed(report.getFailed() + 1);
		}
		report.getItems().add(item);
		if (!item.isOk() && stopOnFailure) {
			report.setStopped(true);
		}
	}

	public ChangeResult importCurl(String collectionId, String content, String folderId) {
		RequestCollection parsed = CurlParser.collection(content);
		String firstId = parsed.getRequests().isEmpty() ? null : parsed.getRequests().get(0).getId();
		Workspace workspace = store.update(data -> {
			RequestCollection collection = findCollection(data, collectionId);
			int position = collection.getRequests().size();
			for (ApiRequest request : parsed.getRequests()) {
				request.setFolderId(folderId == null ? "" : folderId);
				request.setPosition(position++);
				collection.getRequests().add(request);
			}
		});
		return new ChangeResult(workspace, firstId);
	}

	public ChangeResult clearHistory() {
		return new ChangeResult(store.update(data -> data.setHistory(new ArrayList<>())), null);
	}

	public ChangeResult clearCookies() {
		return new ChangeResult(store.update(data -> data.getCookies().clear()), null);
	}

	public ChangeResult deleteCookie(String domain, String name) {
		Workspace workspace = store.update(data -> data.getCookies().removeIf(cookie ->
			domain != null && domain.equals(cookie.getDomain()) && name != null && name.equals(cookie.getName())));
		return new ChangeResult(workspace, null);
	}

	public ChangeResult addCookie(StoredCookie cookie) {
		if (cookie == null || cookie.getDomain() == null || cookie.getDomain().isBlank()) {
			throw new IllegalArgumentException("Cookie domain is required");
		}
		Workspace workspace = store.update(data -> CookieJar.remember(data.getCookies(), cookie));
		return new ChangeResult(workspace, null);
	}

	private void remember(Workspace data, ExecuteCommand command, ExecuteResult result, List<StoredCookie> cookies, ApiRequest request) {
		data.setCookies(cookies);
		applyExtractors(data, command, result.getBody());
		HistoryEntry entry = new HistoryEntry();
		entry.setId(UUID.randomUUID().toString());
		entry.setRequestId(command.getRequestId() == null ? "" : command.getRequestId());
		entry.setRequestName(command.getRequestName() == null || command.getRequestName().isBlank() ? command.getUrl() : command.getRequestName());
		entry.setMethod(command.getMethod());
		entry.setUrl(scrub(command.getUrl(), data, command));
		entry.setStatus(result.getStatus());
		entry.setTimeMs(result.getTimeMs());
		entry.setOk(result.isOk());
		entry.setAt(Instant.now().toString());
		if (data.getSettings().isStoreHistoryBodies()) {
			entry.setRequestBody(truncate(command.getBody(), 8000));
			entry.setResponseBody(truncate(result.getBody(), 8000));
		}
		if (command.getRequestId() != null && !command.getRequestId().isBlank()
				&& (!command.getExtras().getOauthAccessToken().isBlank() || !command.getExtras().getOauthRefreshToken().isBlank())) {
			try {
				com.apiflow.model.RequestExtras saved = findRequest(data, command.getRequestId()).getExtras();
				saved.setOauthAccessToken(command.getExtras().getOauthAccessToken());
				saved.setOauthRefreshToken(command.getExtras().getOauthRefreshToken());
				saved.setOauthExpiresAt(command.getExtras().getOauthExpiresAt());
			}
			catch (NotFoundException ignored) {
				// The request was sent without being saved.
			}
		}
		data.getHistory().add(0, entry);
		if (data.getHistory().size() > 50) {
			data.setHistory(new ArrayList<>(data.getHistory().subList(0, 50)));
		}
	}

	private static void applyExtractors(Workspace data, ExecuteCommand command, String body) {
		if (command.getExtractors() == null) {
			return;
		}
		for (Extractor extractor : command.getExtractors()) {
			if (extractor == null || !extractor.isEnabled() || extractor.getVariable() == null || extractor.getVariable().isBlank()) {
				continue;
			}
			String value = Checks.read(body, extractor.getPath());
			if (value == null) {
				continue;
			}
			if ("environment".equals(extractor.getScope())) {
				Environment environment = environment(data, command.getEnvironmentId());
				if (environment != null) {
					upsert(environment.getVariables(), extractor.getVariable(), value);
				}
			}
			else {
				RequestCollection collection = collectionOrNull(data, command.getCollectionId());
				if (collection != null) {
					upsert(collection.getVariables(), extractor.getVariable(), value);
				}
			}
		}
	}

	private static void upsert(List<KeyValue> variables, String key, String value) {
		for (KeyValue variable : variables) {
			if (key.equals(variable.getKey())) {
				variable.setValue(value);
				variable.setEnabled(true);
				return;
			}
		}
		variables.add(new KeyValue(key, value, true));
	}

	private static void persistVars(Workspace data, ExecuteCommand command, Map<String, String> before, Map<String, String> after) {
		RequestCollection collection = collectionOrNull(data, command.getCollectionId());
		Environment environment = command.getEnvironmentId() == null || command.getEnvironmentId().isBlank()
			? null
			: data.getEnvironments().stream().filter(item -> command.getEnvironmentId().equals(item.getId())).findFirst().orElse(null);
		ScriptScope.State scope = ScriptScope.current();
		for (Map.Entry<String, String> entry : after.entrySet()) {
			if (before.containsKey(entry.getKey()) && entry.getValue().equals(before.get(entry.getKey()))) {
				continue;
			}
			if (scope != null && scope.isEnv(entry.getKey()) && environment != null) {
				upsert(environment.getVariables(), entry.getKey(), entry.getValue());
			}
			else if (scope != null && scope.isCollection(entry.getKey()) && collection != null) {
				upsert(collection.getVariables(), entry.getKey(), entry.getValue());
			}
			else if (environment != null && hasKey(environment.getVariables(), entry.getKey())) {
				upsert(environment.getVariables(), entry.getKey(), entry.getValue());
			}
			else if (collection != null) {
				upsert(collection.getVariables(), entry.getKey(), entry.getValue());
			}
		}
	}

	private static boolean hasKey(List<KeyValue> variables, String key) {
		for (KeyValue variable : variables) {
			if (key.equals(variable.getKey())) {
				return true;
			}
		}
		return false;
	}

	private static String scrub(String url, Workspace data, ExecuteCommand command) {
		if (url == null) {
			return "";
		}
		List<String> secrets = new ArrayList<>();
		RequestCollection collection = collectionOrNull(data, command.getCollectionId());
		if (collection != null) {
			collectSecrets(secrets, collection.getVariables());
		}
		if (command.getEnvironmentId() != null) {
			for (Environment environment : data.getEnvironments()) {
				if (command.getEnvironmentId().equals(environment.getId())) {
					collectSecrets(secrets, environment.getVariables());
				}
			}
		}
		secrets.sort((left, right) -> Integer.compare(right.length(), left.length()));
		String cleaned = url;
		for (String secret : secrets) {
			cleaned = cleaned.replace(secret, "••••");
		}
		return cleaned;
	}

	private static void collectSecrets(List<String> secrets, List<KeyValue> variables) {
		for (KeyValue variable : variables) {
			if (variable.isSecret() && variable.getValue() != null && variable.getValue().length() >= 4) {
				secrets.add(variable.getValue());
			}
		}
	}

	private static RunItem runItem(ApiRequest request, ExecuteResult result, String label, List<String> secrets) {
		RunItem item = new RunItem();
		item.setRequestId(request.getId());
		item.setName(label == null || label.isBlank() ? request.getName() : request.getName() + " · " + label);
		item.setMethod(request.getMethod());
		item.setStatus(result.getStatus());
		item.setError(SecretMasker.mask(result.getError() == null ? "" : result.getError(), secrets));
		String body = result.getBody() == null ? "" : result.getBody();
		if (body.length() > 8000) {
			body = body.substring(0, 8000) + "…";
		}
		item.setResponseBody(SecretMasker.mask(body, secrets));
		item.setChecks(SecretMasker.maskChecks(result.getChecks(), secrets));
		boolean checksPassed = result.getChecks().stream().allMatch(com.apiflow.model.CheckResult::isPassed);
		if (!result.isOk()) {
			item.setOk(false);
		}
		else if (!result.getChecks().isEmpty()) {
			item.setOk(checksPassed);
		}
		else {
			item.setOk(result.getStatus() < 400);
		}
		return item;
	}

	public ExecuteCommand commandFromRequest(RequestCollection collection, ApiRequest request, String environmentId, String globalEnvironmentId) {
		return commandFrom(request, collection.getId(), environmentId, globalEnvironmentId);
	}

	public ExecuteResult executeRequest(String collectionId, String requestId, String environmentId, String globalEnvironmentId, Map<String, String> extra) {
		Workspace snapshot = store.copy();
		RequestCollection collection = findCollection(snapshot, collectionId);
		ApiRequest request = collection.getRequests().stream().filter(item -> requestId.equals(item.getId())).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Request not found"));
		ExecuteCommand command = commandFrom(request, collectionId, environmentId, globalEnvironmentId);
		return execute(command, extra == null ? Map.of() : extra);
	}

	static ExecuteCommand commandFrom(ApiRequest request, String collectionId, String environmentId) {
		return commandFrom(request, collectionId, environmentId, "");
	}

	static ExecuteCommand commandFrom(ApiRequest request, String collectionId, String environmentId, String globalEnvironmentId) {
		ExecuteCommand command = new ExecuteCommand();
		command.setCollectionId(collectionId);
		command.setEnvironmentId(environmentId);
		command.setGlobalEnvironmentId(globalEnvironmentId);
		command.setRequestId(request.getId());
		command.setRequestName(request.getName());
		command.setFolderId(request.getFolderId());
		command.setMethod(request.getMethod());
		command.setUrl(request.getUrl());
		command.setParams(request.getParams());
		command.setHeaders(request.getHeaders());
		command.setBodyType(request.getBodyType());
		command.setBody(request.getBody());
		command.setForm(request.getForm());
		command.setAuthType(request.getAuthType());
		command.setAuthToken(request.getAuthToken());
		command.setAuthUsername(request.getAuthUsername());
		command.setAuthPassword(request.getAuthPassword());
		command.setTimeoutSeconds(request.getTimeoutSeconds());
		command.setFollowRedirects(request.isFollowRedirects());
		command.setGraphqlQuery(request.getGraphqlQuery());
		command.setGraphqlVariables(request.getGraphqlVariables());
		command.setFiles(request.getFiles());
		command.setApiKeyName(request.getApiKeyName());
		command.setApiKeyValue(request.getApiKeyValue());
		command.setApiKeyIn(request.getApiKeyIn());
		command.setPreRequestScript(request.getPreRequestScript());
		command.setPostResponseScript(request.getPostResponseScript());
		command.setAssertions(request.getAssertions());
		command.setExtractors(request.getExtractors());
		command.setExtras(request.getExtras().copy());
		command.setTags(request.getTags());
		return command;
	}

	private static ApiRequest requestOrNull(Workspace workspace, String requestId) {
		if (requestId == null || requestId.isBlank()) {
			return null;
		}
		try {
			return findRequest(workspace, requestId);
		}
		catch (NotFoundException ex) {
			return null;
		}
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max) + "\n…";
	}

	private static void putAll(Map<String, String> variables, List<KeyValue> source) {
		for (KeyValue variable : source) {
			if (variable != null && variable.isEnabled() && variable.getKey() != null && !variable.getKey().isBlank()) {
				variables.put(variable.getKey().trim(), variable.getValue() == null ? "" : variable.getValue());
			}
		}
	}

	private static void copyRequest(ApiRequest target, ApiRequest incoming) {
		target.setName(cleanName(incoming.getName(), "Untitled Request"));
		target.setMethod(normalize(incoming.getMethod(), METHODS, "GET"));
		target.setUrl(incoming.getUrl() == null ? "" : incoming.getUrl());
		target.setParams(incoming.getParams());
		target.setHeaders(incoming.getHeaders());
		target.setBodyType(normalize(incoming.getBodyType(), BODY_TYPES, "none"));
		target.setBody(incoming.getBody() == null ? "" : incoming.getBody());
		target.setForm(incoming.getForm());
		target.setAuthType(normalize(incoming.getAuthType(), AUTH_TYPES, "none"));
		target.setAuthToken(incoming.getAuthToken() == null ? "" : incoming.getAuthToken());
		target.setAuthUsername(incoming.getAuthUsername() == null ? "" : incoming.getAuthUsername());
		target.setAuthPassword(incoming.getAuthPassword() == null ? "" : incoming.getAuthPassword());
		target.setFolderId(incoming.getFolderId());
		target.setPosition(incoming.getPosition());
		target.setTimeoutSeconds(incoming.getTimeoutSeconds());
		target.setFollowRedirects(incoming.isFollowRedirects());
		target.setGraphqlQuery(incoming.getGraphqlQuery() == null ? "" : incoming.getGraphqlQuery());
		target.setGraphqlVariables(incoming.getGraphqlVariables() == null ? "" : incoming.getGraphqlVariables());
		target.setFiles(incoming.getFiles());
		target.setApiKeyName(incoming.getApiKeyName() == null ? "" : incoming.getApiKeyName());
		target.setApiKeyValue(incoming.getApiKeyValue() == null ? "" : incoming.getApiKeyValue());
		target.setApiKeyIn(incoming.getApiKeyIn());
		target.setPreRequestScript(incoming.getPreRequestScript() == null ? "" : incoming.getPreRequestScript());
		target.setPostResponseScript(incoming.getPostResponseScript());
		target.setExampleBody(incoming.getExampleBody());
		target.setExampleContentType(incoming.getExampleContentType());
		target.setVariables(incoming.getVariables());
		target.setExamples(incoming.getExamples());
		target.setDocs(incoming.getDocs());
		target.setTags(incoming.getTags());
		target.setExtras(incoming.getExtras().copy());
		target.setAssertions(incoming.getAssertions());
		target.setExtractors(incoming.getExtractors());
	}

	private static boolean isDescendant(RequestCollection collection, String folderId, String ancestorId) {
		String current = folderId;
		while (current != null && !current.isBlank()) {
			if (ancestorId.equals(current)) {
				return true;
			}
			String parent = "";
			for (Folder folder : collection.getFolders()) {
				if (current.equals(folder.getId())) {
					parent = folder.getParentId();
				}
			}
			if (parent.equals(current)) {
				return false;
			}
			current = parent;
		}
		return false;
	}

	private static RequestCollection findCollection(Workspace workspace, String id) {
		return workspace.getCollections()
			.stream()
			.filter(collection -> id.equals(collection.getId()))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Collection not found"));
	}

	private static RequestCollection collectionOrNull(Workspace workspace, String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return workspace.getCollections().stream().filter(collection -> id.equals(collection.getId())).findFirst().orElse(null);
	}

	private static RequestCollection findCollectionOf(Workspace workspace, String requestId) {
		for (RequestCollection collection : workspace.getCollections()) {
			for (ApiRequest request : collection.getRequests()) {
				if (requestId.equals(request.getId())) {
					return collection;
				}
			}
		}
		throw new NotFoundException("Request not found");
	}

	private static ApiRequest findRequest(Workspace workspace, String id) {
		for (RequestCollection collection : workspace.getCollections()) {
			for (ApiRequest request : collection.getRequests()) {
				if (id.equals(request.getId())) {
					return request;
				}
			}
		}
		throw new NotFoundException("Request not found");
	}

	private static Environment findEnvironment(Workspace workspace, String id) {
		return workspace.getEnvironments()
			.stream()
			.filter(environment -> id.equals(environment.getId()))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Environment not found"));
	}

	private static Environment environment(Workspace workspace, String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return workspace.getEnvironments().stream().filter(environment -> id.equals(environment.getId())).findFirst()
			.orElseThrow(() -> new NotFoundException("Environment not found"));
	}

	private static String cleanName(String name, String fallback) {
		if (name == null || name.isBlank()) {
			return fallback;
		}
		String trimmed = name.trim();
		return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
	}

	private static String requireName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Name is required");
		}
		return cleanName(name, "");
	}

	private static String normalize(String value, Set<String> allowed, String fallback) {
		if (value == null) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (METHODS.contains(fallback)) {
			normalized = value.trim().toUpperCase(Locale.ROOT);
		}
		return allowed.contains(normalized) ? normalized : fallback;
	}

	public ChangeResult syncOpenApi(String collectionId, String content) {
		return syncOpenApi(collectionId, content, "additive", false);
	}

	public ChangeResult syncOpenApi(String collectionId, String content, String mode, boolean deleteStale) {
		Workspace workspace = store.update(data -> {
			RequestCollection collection = findCollection(data, collectionId);
			openApiSync.syncFromSpec(collection, content, mode, deleteStale);
		});
		return new ChangeResult(workspace, collectionId);
	}

	public OpenApiSyncService.OpenApiDiff openApiDiff(String collectionId, String content) {
		RequestCollection collection = exportCollection(collectionId);
		return openApiSync.diff(collection, content);
	}

	public String exportOpenCollection(String collectionId) {
		return openCollection.exportYaml(exportCollection(collectionId));
	}

	public java.util.List<String> secretValues() {
		java.util.List<String> secrets = new java.util.ArrayList<>();
		Workspace workspace = store.copy();
		collectSecrets(secrets, workspace.getVariables());
		for (Environment environment : workspace.getEnvironments()) {
			collectSecrets(secrets, environment.getVariables());
		}
		for (RequestCollection collection : workspace.getCollections()) {
			collectSecrets(secrets, collection.getVariables());
		}
		return secrets;
	}

	public String suggestScript(String prompt, String context) {
		Workspace workspace = store.copy();
		return ai.chat(workspace.getSettings(), prompt, context, "script");
	}

	public String aiChat(String prompt, String context) {
		return aiChat(prompt, context, null);
	}

	public String aiChat(String prompt, String context, String sessionId) {
		Workspace workspace = store.copy();
		String session = ai.ensureSession(sessionId);
		return ai.chat(workspace.getSettings(), prompt, context, "assistant", session, ai.history(session));
	}

	public java.util.List<AiConversationStore.AiMessage> aiHistory(String sessionId) {
		return ai.history(sessionId);
	}

}
