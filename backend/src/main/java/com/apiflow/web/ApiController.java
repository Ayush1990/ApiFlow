package com.apiflow.web;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.ChangeResult;
import com.apiflow.model.Environment;
import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.Folder;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.RunReport;
import com.apiflow.model.Workspace;
import com.apiflow.model.StoredCookie;
import com.apiflow.service.AiConversationStore;
import com.apiflow.service.AiService;
import com.apiflow.service.AppsService;
import com.apiflow.service.BrunoFolderImporter;
import com.apiflow.service.DocsService;
import com.apiflow.service.ExportService;
import com.apiflow.service.GitService;
import com.apiflow.service.GraphqlIntrospection;
import com.apiflow.service.BruFolderExporter;
import com.apiflow.service.BrunoParser;
import com.apiflow.service.ImportService;
import com.apiflow.service.SecretMigrationService;
import com.apiflow.service.WorkspaceProfileService;
import com.apiflow.service.GrpcExecutor;
import com.apiflow.service.GrpcStreamHub;
import com.apiflow.service.MockServer;
import com.apiflow.service.NpmPackageService;
import com.apiflow.service.OAuthService;
import com.apiflow.service.OpenApiSyncService;
import com.apiflow.service.ProtoCodec;
import com.apiflow.service.PullRequestService;
import com.apiflow.service.SecretManagerService;
import com.apiflow.service.SseHub;
import com.apiflow.service.WorkspaceBundleService;
import com.apiflow.service.WorkspaceService;
import com.apiflow.service.WsHub;

@RestController
@RequestMapping("/api")
public class ApiController {

	private final WorkspaceService workspaceService;
	private final OAuthService oauthService;
	private final MockServer mockServer;
	private final GitService gitService;
	private final WsHub wsHub;
	private final ExportService exportService;
	private final DocsService docsService;
	private final GraphqlIntrospection graphqlIntrospection;
	private final OpenApiSyncService openApiSyncService;
	private final SecretManagerService secretManagerService;
	private final NpmPackageService npmPackageService;
	private final PullRequestService pullRequestService;
	private final AppsService appsService;
	private final GrpcStreamHub grpcStreamHub;
	private final AiService aiService;
	private final WorkspaceBundleService workspaceBundleService;
	private final SseHub sseHub;
	private final ImportService importService;
	private final SecretMigrationService secretMigrationService;
	private final WorkspaceProfileService workspaceProfileService;

	public ApiController(WorkspaceService workspaceService, OAuthService oauthService, MockServer mockServer, GitService gitService, WsHub wsHub, ExportService exportService, DocsService docsService, GraphqlIntrospection graphqlIntrospection, OpenApiSyncService openApiSyncService, SecretManagerService secretManagerService, NpmPackageService npmPackageService, PullRequestService pullRequestService, AppsService appsService, GrpcStreamHub grpcStreamHub, AiService aiService, WorkspaceBundleService workspaceBundleService, SseHub sseHub, ImportService importService, SecretMigrationService secretMigrationService, WorkspaceProfileService workspaceProfileService) {
		this.workspaceService = workspaceService;
		this.oauthService = oauthService;
		this.mockServer = mockServer;
		this.gitService = gitService;
		this.wsHub = wsHub;
		this.exportService = exportService;
		this.docsService = docsService;
		this.graphqlIntrospection = graphqlIntrospection;
		this.openApiSyncService = openApiSyncService;
		this.secretManagerService = secretManagerService;
		this.npmPackageService = npmPackageService;
		this.pullRequestService = pullRequestService;
		this.appsService = appsService;
		this.grpcStreamHub = grpcStreamHub;
		this.aiService = aiService;
		this.workspaceBundleService = workspaceBundleService;
		this.sseHub = sseHub;
		this.importService = importService;
		this.secretMigrationService = secretMigrationService;
		this.workspaceProfileService = workspaceProfileService;
	}

	@GetMapping("/workspace")
	public Workspace workspace() {
		return workspaceService.workspace();
	}

	@PutMapping("/workspace")
	public ChangeResult updateWorkspace(@RequestBody Workspace body) {
		return workspaceService.updateWorkspace(body);
	}

	@GetMapping(value = "/export/workspace", produces = MediaType.APPLICATION_JSON_VALUE)
	public String exportWorkspace() {
		return exportService.exportWorkspace(workspaceService.workspace());
	}

	@GetMapping(value = "/export/workspace/bundle", produces = MediaType.APPLICATION_JSON_VALUE)
	public String exportWorkspaceBundle() {
		return workspaceBundleService.exportBundle();
	}

	@PostMapping("/import/workspace/bundle")
	public ChangeResult importWorkspaceBundle(@RequestBody ImportBody body) {
		Workspace workspace = workspaceBundleService.importBundle(body == null ? null : body.content());
		return new ChangeResult(workspace, null);
	}

	@PostMapping("/collections")
	public ChangeResult createCollection(@RequestBody NameBody body) {
		return workspaceService.createCollection(body == null ? null : body.name());
	}

	@PatchMapping("/collections/{id}")
	public ChangeResult renameCollection(@PathVariable String id, @RequestBody NameBody body) {
		return workspaceService.renameCollection(id, body == null ? null : body.name());
	}

	@PutMapping("/collections/{id}")
	public ChangeResult updateCollection(@PathVariable String id, @RequestBody RequestCollection body) {
		return workspaceService.updateCollection(id, body);
	}

	@GetMapping("/collections/{id}/export")
	public RequestCollection exportCollection(@PathVariable String id) {
		return workspaceService.exportCollection(id);
	}

	@GetMapping(value = "/collections/{id}/export/bruno", produces = "text/plain")
	public String exportBruno(@PathVariable String id) {
		return exportService.exportBruno(workspaceService.exportCollection(id));
	}

	@GetMapping(value = "/collections/{id}/export/bruno-folder", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.List<BruFolderExporter.BruFileEntry> exportBrunoFolder(@PathVariable String id) {
		return BruFolderExporter.export(workspaceService.exportCollection(id));
	}

	@GetMapping(value = "/collections/{id}/export/postman", produces = MediaType.APPLICATION_JSON_VALUE)
	public String exportPostman(@PathVariable String id) {
		return exportService.exportPostman(workspaceService.exportCollection(id));
	}

	@GetMapping(value = "/collections/{id}/export/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
	public String exportOpenApi(@PathVariable String id) {
		return openApiSyncService.exportMerged(workspaceService.exportCollection(id));
	}

	@GetMapping(value = "/collections/{id}/export/opencollection", produces = "text/yaml")
	public String exportOpenCollection(@PathVariable String id) {
		return workspaceService.exportOpenCollection(id);
	}

	@PostMapping("/collections/{id}/openapi/sync")
	public ChangeResult syncOpenApi(@PathVariable String id, @RequestBody(required = false) OpenApiSyncBody body) {
		return workspaceService.syncOpenApi(
			id,
			body == null ? "" : body.content(),
			body == null ? "additive" : body.mode(),
			body != null && Boolean.TRUE.equals(body.deleteStale())
		);
	}

	@PostMapping("/collections/{id}/openapi/diff")
	public OpenApiSyncService.OpenApiDiff openApiDiff(@PathVariable String id, @RequestBody(required = false) TextBody body) {
		return workspaceService.openApiDiff(id, body == null ? "" : body.content());
	}

	@PostMapping("/grpc/reflect")
	public java.util.List<String> grpcReflect(@RequestBody(required = false) GrpcReflectBody body) throws Exception {
		if (body != null && body.proto() != null && !body.proto().isBlank()) {
			return GrpcExecutor.reflectMethods(body.url(), body.proto());
		}
		return GrpcExecutor.reflect(body == null ? "" : body.url());
	}

	@PostMapping("/grpc/encode")
	public GrpcCodecBody grpcEncode(@RequestBody(required = false) GrpcCodecBody body) throws Exception {
		byte[] bytes = ProtoCodec.encode(body == null ? "" : body.proto(), body == null ? "" : body.method(), body == null ? "" : body.json());
		return new GrpcCodecBody(body == null ? "" : body.proto(), body == null ? "" : body.method(), body == null ? "" : body.json(), java.util.Base64.getEncoder().encodeToString(bytes));
	}

	@PostMapping("/grpc/decode")
	public GrpcCodecBody grpcDecode(@RequestBody(required = false) GrpcCodecBody body) throws Exception {
		byte[] bytes = java.util.Base64.getDecoder().decode(body == null ? "" : body.encoded());
		String json = ProtoCodec.decode(body == null ? "" : body.proto(), body == null ? "" : body.method(), bytes, true);
		return new GrpcCodecBody(body == null ? "" : body.proto(), body == null ? "" : body.method(), json, body == null ? "" : body.encoded());
	}

	@GetMapping("/scripts/npm")
	public NpmPackageService.InstallResult npmStatus() {
		return new NpmPackageService.InstallResult(npmPackageService.listPackages(), npmPackageService.packageJson());
	}

	@PutMapping("/scripts/npm/package")
	public String npmPackage(@RequestBody(required = false) TextBody body) {
		return npmPackageService.savePackageJson(body == null ? "" : body.content());
	}

	@PostMapping("/scripts/npm/install")
	public NpmPackageService.InstallResult npmInstall() {
		return npmPackageService.install();
	}

	@PostMapping("/scripts/npm/add")
	public NpmPackageService.InstallResult npmAdd(@RequestBody(required = false) NameBody body) {
		return npmPackageService.add(body == null ? "" : body.name());
	}

	@GetMapping("/git/prs")
	public java.util.List<PullRequestService.PullRequest> gitPullRequests(@RequestParam(required = false) String remote) {
		var identity = workspaceService.workspace().getSettings().getIdentity();
		return pullRequestService.list(remote == null ? "" : remote, identity);
	}

	@PostMapping("/git/pr/create")
	public PullRequestService.PullRequest gitCreatePr(@RequestBody(required = false) PullRequestBody body) {
		var identity = workspaceService.workspace().getSettings().getIdentity();
		return pullRequestService.create(
			body == null ? "" : body.remote(),
			identity,
			body == null ? "" : body.title(),
			body == null ? "" : body.body(),
			body == null ? "" : body.head(),
			body == null ? "" : body.base()
		);
	}

	@PostMapping("/git/pr/merge")
	public String gitMergePr(@RequestBody(required = false) PullRequestBody body) {
		var identity = workspaceService.workspace().getSettings().getIdentity();
		return pullRequestService.merge(body == null ? "" : body.remote(), identity, body == null || body.number() == null ? 0 : body.number());
	}

	@GetMapping("/apps")
	public java.util.List<AppsService.AppManifest> apps() {
		return appsService.list();
	}

	@PostMapping("/apps")
	public AppsService.AppManifest saveApp(@RequestBody AppsService.AppManifest manifest) {
		return appsService.save(manifest);
	}

	@PostMapping("/apps/{id}/run")
	public RunReport runApp(@PathVariable String id, @RequestBody(required = false) RunBody body) {
		return appsService.run(id, body == null ? "" : body.environmentId());
	}

	@PostMapping("/apps/{id}/submit")
	public ExecuteResult submitAppRequest(@PathVariable String id, @RequestBody(required = false) ExecuteCommand body) {
		return appsService.submitRequest(id, body, body == null ? "" : body.getEnvironmentId());
	}

	@PostMapping("/apps/{id}/run-request")
	public ExecuteResult runAppRequest(@PathVariable String id, @RequestBody(required = false) AppRequestBody body) {
		return appsService.runRequest(id, body == null ? "" : body.requestName(), body == null ? "" : body.environmentId());
	}

	@PostMapping("/apps/{id}/set-var")
	public ChangeResult setAppVar(@PathVariable String id, @RequestBody(required = false) AppVarBody body) {
		appsService.setVar(id, body == null ? "" : body.name(), body == null ? "" : body.value(), body == null ? "" : body.environmentId(), body != null && Boolean.TRUE.equals(body.envScope()));
		return new ChangeResult(workspaceService.workspace(), null);
	}

	@PostMapping("/bru/parse")
	public ApiRequest parseBru(@RequestBody(required = false) TextBody body) {
		return BrunoParser.request(body == null ? "" : body.content());
	}

	@DeleteMapping("/apps/{id}")
	public void deleteApp(@PathVariable String id) {
		appsService.delete(id);
	}

	@PostMapping("/apps/generate")
	public AiResponse generateApp(@RequestBody(required = false) AiBody body) {
		String html = appsService.generateHtml(body == null ? "" : body.prompt(), body == null ? "" : body.context());
		return new AiResponse(html);
	}

	@PostMapping("/secrets/resolve")
	public SecretBody resolveSecret(@RequestBody(required = false) SecretBody body) {
		var settings = workspaceService.workspace().getSettings().getSecretManager();
		String value = secretManagerService.resolve(body == null ? "" : body.reference(), settings);
		return new SecretBody(body == null ? "" : body.reference(), value);
	}

	@PostMapping("/secrets/clear-cache")
	public void clearSecretCache() {
		secretManagerService.clearCache();
	}

	@PostMapping("/ai/script")
	public AiResponse aiScript(@RequestBody(required = false) AiBody body) {
		String script = workspaceService.suggestScript(body == null ? "" : body.prompt(), body == null ? "" : body.context());
		return new AiResponse(script);
	}

	@PostMapping("/ai/chat")
	public AiResponse aiChat(@RequestBody(required = false) AiBody body) {
		String reply = workspaceService.aiChat(body == null ? "" : body.prompt(), body == null ? "" : body.context(), body == null ? null : body.sessionId());
		return new AiResponse(reply);
	}

	@GetMapping("/ai/history")
	public java.util.List<AiConversationStore.AiMessage> aiHistory(@RequestParam(required = false) String session) {
		return workspaceService.aiHistory(session == null ? "" : session);
	}

	@PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter aiChatStream(@RequestBody(required = false) AiBody body) {
		SseEmitter emitter = new SseEmitter(120_000L);
		var settings = workspaceService.workspace().getSettings();
		String session = aiService.ensureSession(body == null ? null : body.sessionId());
		new Thread(() -> {
			try {
				aiService.chatStream(
					settings,
					body == null ? "" : body.prompt(),
					body == null ? "" : body.context(),
					"assistant",
					session,
					chunk -> {
						try {
							emitter.send(SseEmitter.event().data(chunk));
						}
						catch (IOException ex) {
							throw new RuntimeException(ex);
						}
					}
				);
				emitter.send(SseEmitter.event().data("[DONE]"));
				emitter.complete();
			}
			catch (Exception ex) {
				emitter.completeWithError(ex);
			}
		}, "ai-stream").start();
		return emitter;
	}

	@GetMapping("/git/conflicts")
	public java.util.List<GitService.ConflictFile> gitConflicts() {
		return gitService.conflicts();
	}

	@PostMapping("/git/conflicts/resolve")
	public GitService.GitStatus gitResolveConflict(@RequestBody(required = false) ConflictResolveBody body) {
		return gitService.resolveConflict(
			body == null ? "" : body.path(),
			body == null || body.blockIndex() == null ? 0 : body.blockIndex(),
			body == null ? "" : body.choice()
		);
	}

	@GetMapping(value = "/collections/{id}/docs/html", produces = MediaType.TEXT_HTML_VALUE)
	public String collectionDocs(@PathVariable String id) {
		Workspace workspace = workspaceService.workspace();
		java.util.List<Environment> envs = workspace.getEnvironments().stream().filter(Environment::isGlobal).toList();
		return docsService.html(workspaceService.exportCollection(id), envs);
	}

	@PostMapping("/collections/{id}/docs/generate")
	public ChangeResult generateCollectionDocs(@PathVariable String id) {
		return workspaceService.updateCollectionDocs(id, docsService.autoGenerateDocs(workspaceService.exportCollection(id)));
	}

	@PostMapping("/collections/{id}/docs/deploy")
	public DocsService.DeployResult deployCollectionDocs(@PathVariable String id) {
		Workspace workspace = workspaceService.workspace();
		java.util.List<Environment> envs = workspace.getEnvironments().stream().filter(Environment::isGlobal).toList();
		return docsService.deploy(workspaceService.exportCollection(id), envs);
	}

	@GetMapping(value = "/collections/{id}/docs/bundle", produces = "application/zip")
	public org.springframework.http.ResponseEntity<byte[]> exportDocsBundle(@PathVariable String id) {
		Workspace workspace = workspaceService.workspace();
		java.util.List<Environment> envs = workspace.getEnvironments().stream().filter(Environment::isGlobal).toList();
		byte[] zip = docsService.exportStaticBundle(workspaceService.exportCollection(id), envs);
		String filename = (workspaceService.exportCollection(id).getName() == null ? "docs" : workspaceService.exportCollection(id).getName()) + "-docs.zip";
		return org.springframework.http.ResponseEntity.ok()
			.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "") + "\"")
			.body(zip);
	}

	@GetMapping(value = "/docs/{id}", produces = MediaType.TEXT_HTML_VALUE)
	public String deployedDocs(@PathVariable String id) {
		return docsService.readDeployed(id);
	}

	@PostMapping("/import/postman/organize")
	public java.util.Map<String, Object> organizePostman(@RequestBody(required = false) ImportBody body) {
		return importService.organizePostman(body == null ? null : body.content());
	}

	@PostMapping("/import/postman/scripts")
	public java.util.List<java.util.Map<String, String>> previewPostmanScripts(@RequestBody(required = false) ImportBody body) {
		return importService.previewPostmanScripts(body == null ? null : body.content());
	}

	@GetMapping("/secrets/migrate/preview")
	public java.util.Map<String, Object> secretMigrationPreview() {
		return secretMigrationService.preview();
	}

	@PostMapping("/secrets/migrate")
	public ChangeResult secretMigration(@RequestBody(required = false) SecretMigrateBody body) {
		Workspace workspace = secretMigrationService.migrate(body == null ? "" : body.environmentId());
		return new ChangeResult(workspace, null);
	}

	@GetMapping("/profiles")
	public java.util.List<String> listProfiles() {
		return workspaceProfileService.list();
	}

	@PostMapping("/profiles/{name}")
	public void saveProfile(@PathVariable String name) {
		workspaceProfileService.saveCurrent(name);
	}

	@PostMapping("/profiles/{name}/load")
	public ChangeResult loadProfile(@PathVariable String name) {
		Workspace workspace = workspaceProfileService.load(name);
		return new ChangeResult(workspace, null);
	}

	@GetMapping(value = "/environments/{id}/export", produces = "text/plain")
	public String exportEnvironment(@PathVariable String id) {
		Environment environment = workspaceService.workspace().getEnvironments().stream().filter(item -> id.equals(item.getId())).findFirst()
			.orElseThrow(() -> new com.apiflow.service.NotFoundException("Environment not found"));
		return exportService.exportEnvironment(environment);
	}

	@PostMapping("/collections/{id}/curl")
	public ChangeResult importCurl(@PathVariable String id, @RequestBody(required = false) ImportBody body) {
		return workspaceService.importCurl(id, body == null ? null : body.content(), body == null ? "" : body.folderId());
	}

	@PostMapping("/import")
	public ChangeResult importDocument(@RequestBody ImportBody body) {
		return workspaceService.importDocument(body == null ? null : body.content());
	}

	@PostMapping("/import/bruno")
	public ChangeResult importBruno(@RequestBody(required = false) BrunoBody body) {
		return workspaceService.importBruno(body == null ? java.util.List.of() : body.files());
	}

	@PostMapping("/collections/{id}/folders")
	public ChangeResult createFolder(@PathVariable String id, @RequestBody(required = false) NameBody body) {
		return workspaceService.createFolder(id, body == null ? null : body.name(), body == null ? null : body.parentId());
	}

	@PatchMapping("/folders/{id}")
	public ChangeResult renameFolder(@PathVariable String id, @RequestBody NameBody body) {
		return workspaceService.renameFolder(id, body == null ? null : body.name());
	}

	@PutMapping("/folders/{id}")
	public ChangeResult updateFolder(@PathVariable String id, @RequestBody Folder body) {
		return workspaceService.updateFolder(id, body);
	}

	@DeleteMapping("/folders/{id}")
	public ChangeResult deleteFolder(@PathVariable String id) {
		return workspaceService.deleteFolder(id);
	}

	@DeleteMapping("/collections/{id}")
	public ChangeResult deleteCollection(@PathVariable String id) {
		return workspaceService.deleteCollection(id);
	}

	@PostMapping("/collections/{id}/requests")
	public ChangeResult createRequest(@PathVariable String id, @RequestBody(required = false) NameBody body) {
		return workspaceService.createRequest(id, body == null ? null : body.name(), body == null ? null : body.folderId());
	}

	@PutMapping("/requests/{id}")
	public ChangeResult updateRequest(@PathVariable String id, @RequestBody ApiRequest request) {
		return workspaceService.updateRequest(id, request);
	}

	@DeleteMapping("/requests/{id}")
	public ChangeResult deleteRequest(@PathVariable String id) {
		return workspaceService.deleteRequest(id);
	}

	@PostMapping("/requests/{id}/duplicate")
	public ChangeResult duplicateRequest(@PathVariable String id) {
		return workspaceService.duplicateRequest(id);
	}

	@PostMapping("/adhoc")
	public ChangeResult createAdhocRequest() {
		return workspaceService.createAdhocRequest();
	}

	@PutMapping("/adhoc/{id}")
	public ChangeResult updateAdhocRequest(@PathVariable String id, @RequestBody ApiRequest request) {
		return workspaceService.updateAdhocRequest(id, request);
	}

	@DeleteMapping("/adhoc/{id}")
	public ChangeResult deleteAdhocRequest(@PathVariable String id) {
		return workspaceService.deleteAdhocRequest(id);
	}

	@PostMapping("/adhoc/{id}/save")
	public ChangeResult saveAdhocToCollection(@PathVariable String id, @RequestBody(required = false) NameBody body) {
		return workspaceService.saveAdhocToCollection(id, body == null ? "" : body.collectionId(), body == null ? null : body.folderId());
	}

	@PutMapping("/collections/{id}/order")
	public ChangeResult reorder(@PathVariable String id, @RequestBody OrderBody body) {
		return workspaceService.reorder(id, body == null ? "" : body.folderId(), body == null || body.requestIds() == null ? java.util.List.of() : body.requestIds());
	}

	@PostMapping("/collections/{id}/run")
	public RunReport runCollection(@PathVariable String id, @RequestBody(required = false) RunBody body) {
		return workspaceService.runCollection(id,
			body == null ? null : body.environmentId(),
			body == null ? "" : body.globalEnvironmentId(),
			body == null ? "" : body.folderId(),
			body != null && Boolean.TRUE.equals(body.stopOnFailure()),
			body == null ? "" : body.dataCsv(),
			body != null && Boolean.TRUE.equals(body.parallel()),
			body == null || body.delayMs() == null ? 0 : body.delayMs(),
			body == null || body.tags() == null ? java.util.List.of() : body.tags(),
			body == null ? "" : body.datasetId(),
			runOptions(body));
	}

	private static WorkspaceService.RunOptions runOptions(RunBody body) {
		WorkspaceService.RunOptions options = new WorkspaceService.RunOptions();
		if (body == null) {
			return options;
		}
		if (body.keepVariables() != null) {
			options.keepVariables = body.keepVariables();
		}
		options.ignoreCookies = Boolean.TRUE.equals(body.ignoreCookies());
		if (body.saveCookies() != null) {
			options.saveCookies = body.saveCookies();
		}
		options.mockBaseUrl = body.mockBaseUrl() == null ? "" : body.mockBaseUrl();
		options.shareResults = Boolean.TRUE.equals(body.shareResults());
		options.quietLogs = Boolean.TRUE.equals(body.quietLogs());
		options.requestIds = body.requestIds();
		return options;
	}

	@PostMapping("/environments")
	public ChangeResult createEnvironment(@RequestBody(required = false) NameBody body) {
		return workspaceService.createEnvironment(body == null ? null : body.name());
	}

	@PutMapping("/environments/{id}")
	public ChangeResult updateEnvironment(@PathVariable String id, @RequestBody Environment environment) {
		return workspaceService.updateEnvironment(id, environment);
	}

	@DeleteMapping("/environments/{id}")
	public ChangeResult deleteEnvironment(@PathVariable String id) {
		return workspaceService.deleteEnvironment(id);
	}

	@DeleteMapping("/history")
	public ChangeResult clearHistory() {
		return workspaceService.clearHistory();
	}

	@DeleteMapping("/cookies")
	public ChangeResult clearCookies() {
		return workspaceService.clearCookies();
	}

	@PostMapping("/cookies/remove")
	public ChangeResult deleteCookie(@RequestBody(required = false) CookieBody body) {
		return workspaceService.deleteCookie(body == null ? "" : body.domain(), body == null ? "" : body.name());
	}

	@PostMapping("/cookies")
	public ChangeResult addCookie(@RequestBody StoredCookie cookie) {
		return workspaceService.addCookie(cookie);
	}

	@PostMapping("/graphql/introspect")
	public String graphqlIntrospect(@RequestBody GraphqlBody body) {
		return graphqlIntrospection.introspect(body == null ? "" : body.url(), body == null ? java.util.List.of() : body.headers());
	}

	@PostMapping("/execute")
	public ExecuteResult execute(@RequestBody ExecuteCommand command) {
		return workspaceService.execute(command);
	}

	@PostMapping("/oauth/start")
	public OAuthService.Started oauthStart(@RequestBody(required = false) OAuthStart body) {
		return oauthService.start(body == null ? "" : body.authUrl(), body == null ? "" : body.tokenUrl(), body == null ? "" : body.clientId(), body == null ? "" : body.clientSecret(), body == null ? "" : body.scope());
	}

	@PostMapping("/oauth/device/start")
	public OAuthService.DeviceStarted oauthDeviceStart(@RequestBody(required = false) OAuthDeviceStart body) {
		return oauthService.startDevice(body == null ? "" : body.deviceUrl(), body == null ? "" : body.clientId(), body == null ? "" : body.scope());
	}

	@PostMapping("/oauth/device/poll")
	public OAuthService.TokenResponse oauthDevicePoll(@RequestBody(required = false) OAuthDevicePoll body) {
		return oauthService.pollDevice(body == null ? "" : body.tokenUrl(), body == null ? "" : body.clientId(), body == null ? "" : body.clientSecret(), body == null ? "" : body.deviceCode());
	}

	@GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
	public String oauthCallback(@RequestParam(required = false) String code, @RequestParam(required = false) String state) {
		return oauthService.callback(code, state);
	}

	@GetMapping("/mock")
	public MockServer.MockState mockState() {
		return mockServer.state();
	}

	@GetMapping("/mock/log")
	public java.util.List<MockServer.MockLogEntry> mockLog() {
		return mockServer.log();
	}

	@PostMapping("/collections/{id}/mock")
	public MockServer.MockState startMock(@PathVariable String id, @RequestBody(required = false) PortBody body) {
		int port = body == null || body.port() == null ? 4010 : body.port();
		return mockServer.start(workspaceService.exportCollection(id), port);
	}

	@PostMapping("/collections/{id}/mock/resync")
	public MockServer.MockState resyncMock(@PathVariable String id) {
		return mockServer.resync(id, workspaceService.exportCollection(id));
	}

	@PostMapping("/mock/openapi")
	public MockServer.MockState startOpenApiMock(@RequestBody(required = false) OpenApiMockBody body) {
		int port = body == null || body.port() == null ? 4010 : body.port();
		String key = body == null || body.key() == null || body.key().isBlank() ? "openapi" : body.key();
		return mockServer.startOpenApi(key, body == null ? "" : body.content(), port);
	}

	@PostMapping("/collections/{id}/mock/examples")
	public MockServer.MockState startExampleMock(@PathVariable String id, @RequestBody(required = false) PortBody body) {
		int port = body == null || body.port() == null ? 4010 : body.port();
		return mockServer.startFromExamples(id, workspaceService.exportCollection(id), port);
	}

	@PostMapping("/mock/manual")
	public MockServer.MockState startManualMock(@RequestBody(required = false) ManualMockBody body) {
		int port = body == null || body.port() == null ? 4010 : body.port();
		java.util.List<MockServer.Route> routes = body == null || body.routes() == null ? java.util.List.of() : body.routes().stream()
			.map(item -> new MockServer.Route(item.method(), item.path(), item.query(), item.status(), item.contentType(), item.body(), item.bodyMatch(), item.delayMs(), item.path().contains("*")))
			.toList();
		return mockServer.startManual(body == null || body.key() == null || body.key().isBlank() ? "manual" : body.key(), port, routes);
	}

	@PostMapping("/sse/open")
	public SseHub.OpenResult sseOpen(@RequestBody(required = false) SseOpenBody body) {
		java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
		if (body != null && body.headers() != null) {
			for (KeyValue header : body.headers()) {
				if (header.isEnabled() && header.getKey() != null) {
					headers.put(header.getKey(), header.getValue());
				}
			}
		}
		return sseHub.open(body == null ? "" : body.url(), headers, body == null || body.timeoutSeconds() == null ? 30 : body.timeoutSeconds());
	}

	@GetMapping("/sse/{id}")
	public SseHub.FrameList sseFrames(@PathVariable String id) {
		return sseHub.frames(id);
	}

	@DeleteMapping("/sse/{id}")
	public void sseClose(@PathVariable String id) {
		sseHub.close(id);
	}

	@DeleteMapping("/mock")
	public MockServer.MockState stopMock() {
		return mockServer.stop();
	}

	@GetMapping("/git")
	public GitService.GitStatus gitStatus() {
		return gitService.status();
	}

	@PostMapping("/git/init")
	public GitService.GitStatus gitInit() {
		return gitService.init();
	}

	@PostMapping("/git/commit")
	public GitService.GitStatus gitCommit(@RequestBody(required = false) NameBody body) {
		return gitService.commit(body == null ? "" : body.name());
	}

	@PostMapping("/git/pull")
	public GitService.GitStatus gitPull(@RequestBody(required = false) GitAction body) {
		return gitService.pull(body == null ? "" : body.name());
	}

	@PostMapping("/git/push")
	public GitService.GitStatus gitPush(@RequestBody(required = false) GitAction body) {
		return gitService.push(body == null ? "" : body.name());
	}

	@PostMapping("/git/fetch")
	public GitService.GitStatus gitFetch(@RequestBody(required = false) GitAction body) {
		return gitService.fetch(body == null ? "" : body.name());
	}

	@PostMapping("/git/remote")
	public GitService.GitStatus gitRemote(@RequestBody(required = false) GitAction body) {
		return gitService.remote(body == null ? "" : body.name(), body == null ? "" : body.url());
	}

	@PostMapping("/git/branch")
	public GitService.GitStatus gitBranch(@RequestBody(required = false) GitAction body) {
		return gitService.checkout(body == null ? "" : body.name(), body != null && Boolean.TRUE.equals(body.create()));
	}

	@PostMapping("/git/clone")
	public GitService.GitStatus gitClone(@RequestBody(required = false) GitAction body) {
		return gitService.clone(body == null ? "" : body.url(), body == null ? "" : body.name());
	}

	@PostMapping("/ws/open")
	public WsHub.Opened wsOpen(@RequestBody(required = false) WsOpen body) {
		return wsHub.open(body == null ? "" : body.url(), body == null ? java.util.List.of() : body.headers(), body == null ? null : body.extras(), body == null ? "" : body.text());
	}

	@GetMapping("/ws/{id}")
	public WsHub.Snapshot wsFrames(@PathVariable String id) {
		return wsHub.frames(id);
	}

	@PostMapping("/ws/{id}/send")
	public WsHub.Snapshot wsSend(@PathVariable String id, @RequestBody(required = false) WsText body) {
		if (body != null && body.binary() != null && !body.binary().isBlank()) {
			return wsHub.sendBinary(id, body.binary());
		}
		if (body != null && Boolean.TRUE.equals(body.ping())) {
			return wsHub.ping(id);
		}
		if (body != null && Boolean.TRUE.equals(body.close())) {
			return wsHub.sendCloseFrame(id, body.closeCode() == null ? 1000 : body.closeCode(), body.closeReason());
		}
		return wsHub.send(id, body == null ? "" : body.text());
	}

	@DeleteMapping("/ws/{id}")
	public void wsClose(@PathVariable String id) {
		wsHub.close(id);
	}

	@PostMapping("/grpc/stream/open")
	public GrpcStreamHub.Opened grpcStreamOpen(@RequestBody(required = false) GrpcStreamBody body) {
		ExecuteCommand command = body == null ? new ExecuteCommand() : body.command();
		if (command == null) {
			command = new ExecuteCommand();
		}
		return grpcStreamHub.open(command, body == null ? "" : body.url(), body == null ? "" : body.mode());
	}

	@GetMapping("/grpc/stream/{id}")
	public GrpcStreamHub.Snapshot grpcStreamFrames(@PathVariable String id) {
		return grpcStreamHub.frames(id);
	}

	@PostMapping("/grpc/stream/{id}/send")
	public GrpcStreamHub.Snapshot grpcStreamSend(@PathVariable String id, @RequestBody(required = false) TextBody body) {
		return grpcStreamHub.send(id, body == null ? "" : body.content());
	}

	@PostMapping("/grpc/stream/{id}/finish")
	public GrpcStreamHub.Snapshot grpcStreamFinish(@PathVariable String id) {
		return grpcStreamHub.finish(id);
	}

	@DeleteMapping("/grpc/stream/{id}")
	public void grpcStreamClose(@PathVariable String id) {
		grpcStreamHub.close(id);
	}

	@PostMapping("/cli/run")
	public RunReport cliRun(@RequestBody(required = false) CliBody body) {
		Workspace snapshot = workspaceService.workspace();
		String collectionId = snapshot.getCollections().stream()
			.filter(item -> body != null && (body.collection().equals(item.getId()) || body.collection().equalsIgnoreCase(item.getName())))
			.map(RequestCollection::getId)
			.findFirst()
			.orElse(body == null ? "" : body.collection());
		String environmentId = "";
		if (body != null && body.environment() != null && !body.environment().isBlank()) {
			environmentId = snapshot.getEnvironments().stream()
				.filter(item -> body.environment().equals(item.getId()) || body.environment().equalsIgnoreCase(item.getName()))
				.map(Environment::getId)
				.findFirst()
				.orElse("");
		}
		String globalEnvironmentId = "";
		if (body != null && body.globalEnvironment() != null && !body.globalEnvironment().isBlank()) {
			globalEnvironmentId = snapshot.getEnvironments().stream()
				.filter(item -> body.globalEnvironment().equals(item.getId()) || body.globalEnvironment().equalsIgnoreCase(item.getName()))
				.map(Environment::getId)
				.findFirst()
				.orElse("");
		}
		String folderId = "";
		if (body != null && body.folder() != null && !body.folder().isBlank()) {
			RequestCollection collection = snapshot.getCollections().stream().filter(item -> collectionId.equals(item.getId())).findFirst().orElse(null);
			if (collection != null) {
				folderId = collection.getFolders().stream()
					.filter(item -> body.folder().equals(item.getId()) || body.folder().equalsIgnoreCase(item.getName()))
					.map(Folder::getId)
					.findFirst()
					.orElse("");
			}
		}
		return workspaceService.runCollection(
			collectionId,
			environmentId,
			globalEnvironmentId,
			folderId,
			body != null && Boolean.TRUE.equals(body.stopOnFailure()),
			body == null ? "" : body.dataCsv(),
			body != null && Boolean.TRUE.equals(body.parallel()),
			body == null || body.delayMs() == null ? 0 : body.delayMs(),
			body == null || body.tags() == null ? java.util.List.of() : body.tags()
		);
	}

	@PostMapping(value = "/run/report/html", produces = MediaType.TEXT_HTML_VALUE)
	public String runReportHtml(@RequestBody(required = false) ReportBody body) {
		RunReport report = workspaceService.runNamed(body == null ? "" : body.collection(), body == null ? "" : body.environment(), body == null ? "" : body.folder(), body != null && Boolean.TRUE.equals(body.stopOnFailure()), body == null ? "" : body.dataCsv());
		return com.apiflow.service.ReportGenerator.html(report, body == null ? "Collection run" : body.collection(), workspaceService.secretValues());
	}

	@PostMapping(value = "/run/report/junit", produces = MediaType.APPLICATION_XML_VALUE)
	public String runReportJUnit(@RequestBody(required = false) ReportBody body) {
		RunReport report = workspaceService.runNamed(body == null ? "" : body.collection(), body == null ? "" : body.environment(), body == null ? "" : body.folder(), body != null && Boolean.TRUE.equals(body.stopOnFailure()), body == null ? "" : body.dataCsv());
		return com.apiflow.service.ReportGenerator.junit(report, body == null ? "Collection run" : body.collection(), workspaceService.secretValues());
	}

	public record NameBody(String name, String folderId, String parentId, String collectionId) {
	}

	public record CollectionBody(String name, java.util.List<KeyValue> variables) {
	}

	public record ImportBody(String content, String folderId) {
	}

	public record OrderBody(String folderId, java.util.List<String> requestIds) {
	}

	public record RunBody(String environmentId, String globalEnvironmentId, String folderId, Boolean stopOnFailure, String dataCsv, Boolean parallel, Integer delayMs, java.util.List<String> tags, String datasetId, Boolean keepVariables, Boolean ignoreCookies, Boolean saveCookies, String mockBaseUrl, Boolean shareResults, Boolean quietLogs, java.util.List<String> requestIds) {
	}

	public record CookieBody(String domain, String name) {
	}

	public record BrunoBody(java.util.List<BrunoFolderImporter.BrunoFile> files) {
	}

	public record OAuthStart(String authUrl, String tokenUrl, String clientId, String clientSecret, String scope) {
	}

	public record OAuthDeviceStart(String deviceUrl, String clientId, String scope) {
	}

	public record OAuthDevicePoll(String tokenUrl, String clientId, String clientSecret, String deviceCode) {
	}

	public record OpenApiMockBody(String content, Integer port, String key) {
	}

	public record ManualMockBody(String key, Integer port, java.util.List<ManualRoute> routes) {
	}

	public record ManualRoute(String method, String path, String query, int status, String contentType, String body, String bodyMatch, int delayMs) {
	}

	public record SseOpenBody(String url, java.util.List<KeyValue> headers, Integer timeoutSeconds) {
	}

	public record ReportBody(String collection, String environment, String folder, Boolean stopOnFailure, String dataCsv) {
	}

	public record CliBody(String collection, String environment, String globalEnvironment, String folder, Boolean stopOnFailure, String dataCsv, java.util.List<String> tags, Boolean parallel, Integer delayMs) {
	}

	public record AppRequestBody(String requestName, String environmentId) {
	}

	public record AppVarBody(String name, String value, String environmentId, Boolean envScope) {
	}

	public record GrpcStreamBody(String url, String mode, ExecuteCommand command) {
	}

	public record PortBody(Integer port) {
	}

	public record GitAction(String name, String url, Boolean create) {
	}

	public record WsOpen(String url, java.util.List<KeyValue> headers, com.apiflow.model.RequestExtras extras, String text) {
	}

	public record WsText(String text, String binary, Boolean ping, Boolean close, Integer closeCode, String closeReason) {
	}

	public record SecretMigrateBody(String environmentId) {
	}

	public record GraphqlBody(String url, java.util.List<KeyValue> headers) {
	}

	public record GrpcReflectBody(String url, String proto) {
	}

	public record GrpcCodecBody(String proto, String method, String json, String encoded) {
	}

	public record TextBody(String content) {
	}

	public record PullRequestBody(String remote, String title, String body, String head, String base, Integer number) {
	}

	public record SecretBody(String reference, String value) {
	}

	public record OpenApiSyncBody(String content, String mode, Boolean deleteStale) {
	}

	public record ConflictResolveBody(String path, Integer blockIndex, String choice) {
	}

	public record AiBody(String prompt, String context, String sessionId) {
	}

	public record AiResponse(String script) {
	}

}
