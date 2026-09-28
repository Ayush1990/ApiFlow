package com.apiflow.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.ApiSpec;
import com.apiflow.model.ChangeResult;
import com.apiflow.model.Dataset;
import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.FlowDefinition;
import com.apiflow.model.Monitor;
import com.apiflow.model.MonitorRun;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.Workspace;
import com.apiflow.model.WorkspaceDocument;
import com.apiflow.service.CollectionTypeValidator;
import com.apiflow.service.DatasetService;
import com.apiflow.service.DocsService;
import com.apiflow.service.FlowService;
import com.apiflow.service.MockServer;
import com.apiflow.service.MonitorService;
import com.apiflow.service.MqttHub;
import com.apiflow.service.OpenApiSyncService;
import com.apiflow.service.PerformanceTestService;
import com.apiflow.service.ShareLinkService;
import com.apiflow.service.ShareLinkService.SharePayload;
import com.apiflow.service.SocketIoHub;
import com.apiflow.service.SpecHubService;
import com.apiflow.service.WorkspaceService;
import com.apiflow.store.FileStore;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

	private final WorkspaceService workspaceService;
	private final FileStore store;
	private final SpecHubService specHubService;
	private final MonitorService monitorService;
	private final FlowService flowService;
	private final DatasetService datasetService;
	private final MqttHub mqttHub;
	private final SocketIoHub socketIoHub;
	private final ShareLinkService shareLinkService;
	private final PerformanceTestService performanceTestService;
	private final CollectionTypeValidator typeValidator;
	private final MockServer mockServer;
	private final DocsService docsService;
	private final OpenApiSyncService openApiSyncService;

	public PlatformController(WorkspaceService workspaceService, FileStore store, SpecHubService specHubService, MonitorService monitorService, FlowService flowService, DatasetService datasetService, MqttHub mqttHub, SocketIoHub socketIoHub, ShareLinkService shareLinkService, PerformanceTestService performanceTestService, CollectionTypeValidator typeValidator, MockServer mockServer, DocsService docsService, OpenApiSyncService openApiSyncService) {
		this.workspaceService = workspaceService;
		this.store = store;
		this.specHubService = specHubService;
		this.monitorService = monitorService;
		this.flowService = flowService;
		this.datasetService = datasetService;
		this.mqttHub = mqttHub;
		this.socketIoHub = socketIoHub;
		this.shareLinkService = shareLinkService;
		this.performanceTestService = performanceTestService;
		this.typeValidator = typeValidator;
		this.mockServer = mockServer;
		this.docsService = docsService;
		this.openApiSyncService = openApiSyncService;
	}

	@GetMapping("/specs")
	public List<ApiSpec> specs() {
		return specHubService.list();
	}

	@PostMapping("/specs")
	public ChangeResult saveSpec(@RequestBody ApiSpec spec) {
		return specHubService.save(spec);
	}

	@DeleteMapping("/specs/{id}")
	public ChangeResult deleteSpec(@PathVariable String id) {
		return specHubService.delete(id);
	}

	@PostMapping("/specs/{id}/lint")
	public SpecHubService.GovernanceReport lintSpec(@PathVariable String id) {
		return specHubService.lint(id);
	}

	@GetMapping(value = "/specs/{id}/preview", produces = MediaType.TEXT_HTML_VALUE)
	public String previewSpec(@PathVariable String id) {
		return specHubService.previewHtml(id);
	}

	@PostMapping("/specs/{id}/sync-collection")
	public ChangeResult syncSpecToCollection(@PathVariable String id, @RequestBody(required = false) SyncBody body) {
		ApiSpec spec = specHubService.get(id);
		String collectionId = body == null ? spec.getCollectionId() : body.collectionId();
		return workspaceService.syncOpenApi(collectionId, spec.getContent(), body == null ? "update" : body.mode(), body != null && Boolean.TRUE.equals(body.deleteStale()));
	}

	@PostMapping("/collections/{id}/sync-spec")
	public ChangeResult syncCollectionToSpec(@PathVariable String id) {
		RequestCollection collection = workspaceService.exportCollection(id);
		String content = openApiSyncService.exportMerged(collection);
		ApiSpec spec = specHubService.list().stream().filter(item -> id.equals(item.getCollectionId())).findFirst().orElseGet(ApiSpec::new);
		spec.setName(collection.getName() + " Spec");
		spec.setFormat("openapi");
		spec.setContent(content);
		spec.setCollectionId(id);
		ChangeResult saved = specHubService.save(spec);
		workspaceService.updateCollectionSpecLink(id, spec.getId());
		return saved;
	}

	@GetMapping("/monitors")
	public List<Monitor> monitors() {
		return monitorService.list();
	}

	@PostMapping("/monitors")
	public ChangeResult saveMonitor(@RequestBody Monitor monitor) {
		return monitorService.save(monitor);
	}

	@DeleteMapping("/monitors/{id}")
	public ChangeResult deleteMonitor(@PathVariable String id) {
		return monitorService.delete(id);
	}

	@PostMapping("/monitors/{id}/run")
	public MonitorRun runMonitor(@PathVariable String id) {
		return monitorService.run(id);
	}

	@GetMapping("/monitors/uptime")
	public List<MonitorService.UptimePoint> uptime() {
		return monitorService.uptime();
	}

	@GetMapping("/monitors/runs")
	public List<MonitorRun> monitorRuns(@RequestParam(required = false) String monitorId) {
		return monitorService.history(monitorId);
	}

	@PostMapping("/monitors/runs/{runId}/publish")
	public MonitorRun publishMonitorRun(@PathVariable String runId, HttpServletRequest request) {
		return monitorService.publishRun(runId, requestBaseUrl(request));
	}

	@GetMapping(value = "/monitors/runs/{runId}/report", produces = MediaType.TEXT_HTML_VALUE)
	public String monitorRunReport(@PathVariable String runId) {
		return monitorService.runReportHtml(runId);
	}

	@GetMapping("/flows")
	public List<FlowDefinition> flows() {
		return flowService.list();
	}

	@PostMapping("/flows")
	public ChangeResult saveFlow(@RequestBody FlowDefinition flow) {
		return flowService.save(flow);
	}

	@DeleteMapping("/flows/{id}")
	public ChangeResult deleteFlow(@PathVariable String id) {
		return flowService.delete(id);
	}

	@PostMapping("/flows/{id}/run")
	public FlowService.FlowRunResult runFlow(@PathVariable String id, @RequestBody(required = false) Map<String, String> input) {
		return flowService.run(id, input);
	}

	@GetMapping("/flows/{id}/history")
	public List<FlowService.FlowRunResult> flowHistory(@PathVariable String id) {
		return flowService.history(id);
	}

	@PostMapping("/flows/{id}/deploy")
	public FlowService.DeployResult deployFlow(@PathVariable String id, @RequestBody(required = false) PortBody body) {
		return flowService.deploy(id, body == null ? 0 : body.port());
	}

	@DeleteMapping("/flows/{id}/deploy")
	public ChangeResult undeployFlow(@PathVariable String id) {
		flowService.undeploy(id);
		return new ChangeResult(workspaceService.workspace(), id);
	}

	@PostMapping("/flows/generate")
	public FlowDefinition generateFlow(@RequestBody PromptBody body) {
		return flowService.generateFromPrompt(body == null ? "" : body.prompt());
	}

	@GetMapping("/datasets")
	public List<Dataset> datasets() {
		return datasetService.list();
	}

	@PostMapping("/datasets")
	public ChangeResult saveDataset(@RequestBody Dataset dataset) {
		return datasetService.save(dataset);
	}

	@DeleteMapping("/datasets/{id}")
	public ChangeResult deleteDataset(@PathVariable String id) {
		return datasetService.delete(id);
	}

	@PostMapping("/datasets/{id}/query")
	public DatasetService.QueryResult queryDataset(@PathVariable String id, @RequestBody(required = false) QueryBody body) {
		return datasetService.query(id, body == null ? "" : body.sql());
	}

	@PostMapping("/performance/start")
	public com.apiflow.model.PerformanceRun startPerformance(@RequestBody PerformanceBody body) {
		return performanceTestService.start(body.collectionId(), body.requestId(), body.environmentId(), body.virtualUsers(), body.iterations(), body.rampUpMs(), body.datasetId());
	}

	@GetMapping("/performance/live/{id}")
	public com.apiflow.model.PerformanceRun livePerformance(@PathVariable String id) {
		return performanceTestService.live(id);
	}

	@PostMapping("/performance")
	public com.apiflow.model.PerformanceRun performance(@RequestBody PerformanceBody body) {
		return performanceTestService.run(body.collectionId(), body.requestId(), body.environmentId(), body.virtualUsers(), body.iterations(), body.rampUpMs(), body.datasetId());
	}

	@GetMapping("/performance")
	public java.util.List<com.apiflow.model.PerformanceRun> performanceHistory(@RequestParam(required = false) String collectionId) {
		return performanceTestService.history(collectionId);
	}

	@GetMapping("/performance/compare")
	public PerformanceTestService.CompareResult comparePerformance(@RequestParam String leftId, @RequestParam String rightId) {
		return performanceTestService.compare(leftId, rightId);
	}

	@PostMapping("/share")
	public ShareLinkService.ShareLink share(@RequestBody ShareBody body, HttpServletRequest request) {
		ApiRequest requestModel = body.request();
		ExecuteResult response = body.response();
		return shareLinkService.create(requestModel, response, requestBaseUrl(request));
	}

	private static String requestBaseUrl(HttpServletRequest request) {
		if (request == null) {
			return "http://localhost:8080";
		}
		String forwarded = request.getHeader("X-Forwarded-Proto");
		String scheme = forwarded != null && !forwarded.isBlank() ? forwarded : request.getScheme();
		String host = request.getHeader("X-Forwarded-Host");
		if (host == null || host.isBlank()) {
			host = request.getServerName();
			int port = request.getServerPort();
			if ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443)) {
				host = host + ":" + port;
			}
		}
		return scheme + "://" + host;
	}

	@GetMapping("/share/{id}")
	public SharePayload shared(@PathVariable String id) {
		return shareLinkService.read(id);
	}

	@DeleteMapping("/share/{id}")
	public void revokeShare(@PathVariable String id) {
		shareLinkService.revoke(id);
	}

	@GetMapping(value = "/share/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
	public String shareHtml(@PathVariable String id) {
		SharePayload payload = shareLinkService.read(id);
		return docsService.shareHtml(payload);
	}

	@PostMapping("/mqtt/connect")
	public MqttHub.Opened mqttConnect(@RequestBody MqttBody body) {
		return mqttHub.connect(body.broker(), body.topic(), body.username(), body.password(), body.qos());
	}

	@PostMapping("/mqtt/{id}/publish")
	public MqttHub.Snapshot mqttPublish(@PathVariable String id, @RequestBody MqttPublishBody body) {
		return mqttHub.publish(id, body.topic(), body.payload(), body.qos(), body.retained());
	}

	@GetMapping("/mqtt/{id}/frames")
	public MqttHub.Snapshot mqttFrames(@PathVariable String id) {
		return mqttHub.frames(id);
	}

	@DeleteMapping("/mqtt/{id}")
	public void mqttClose(@PathVariable String id) {
		mqttHub.close(id);
	}

	@PostMapping("/socketio/connect")
	public SocketIoHub.Opened socketConnect(@RequestBody SocketBody body) {
		return socketIoHub.connect(body.url(), body.event());
	}

	@PostMapping("/socketio/{id}/emit")
	public SocketIoHub.Snapshot socketEmit(@PathVariable String id, @RequestBody SocketEmitBody body) {
		return socketIoHub.emit(id, body.event(), body.payload());
	}

	@GetMapping("/socketio/{id}/frames")
	public SocketIoHub.Snapshot socketFrames(@PathVariable String id) {
		return socketIoHub.frames(id);
	}

	@DeleteMapping("/socketio/{id}")
	public void socketClose(@PathVariable String id) {
		socketIoHub.close(id);
	}

	@PostMapping("/collections/{id}/validate")
	public CollectionTypeValidator.ValidationReport validateRequest(@PathVariable String id, @RequestBody ApiRequest request) {
		return typeValidator.validate(workspaceService.exportCollection(id), request);
	}

	@PostMapping("/collections/{id}/mock/public")
	public MockServer.MockState startPublicMock(@PathVariable String id, @RequestBody(required = false) PortBody body) {
		int port = body == null || body.port() == null ? 4010 : body.port();
		return mockServer.start(id, workspaceService.exportCollection(id), port, true);
	}

	@PostMapping("/mock/chaos")
	public MockServer.MockState chaos(@RequestBody ChaosBody body) {
		return mockServer.setChaosMode(body != null && body.enabled());
	}

	@GetMapping("/documents")
	public List<WorkspaceDocument> documents() {
		return workspaceService.workspace().getDocuments();
	}

	@PostMapping("/documents")
	public ChangeResult saveDocument(@RequestBody WorkspaceDocument document) {
		return workspaceService.saveDocument(document);
	}

	@DeleteMapping("/documents/{id}")
	public ChangeResult deleteDocument(@PathVariable String id) {
		return workspaceService.deleteDocument(id);
	}

	@PostMapping("/collections/{id}/docs/publish")
	public DocsService.PublishResult publishDocs(@PathVariable String id) {
		return docsService.publishPublic(workspaceService.exportCollection(id), workspaceService.workspace().getEnvironments());
	}

	@GetMapping("/collections/{id}/docs/embed")
	public DocsService.EmbedResult embedButton(@PathVariable String id) {
		return docsService.embedButton(id);
	}

	@PostMapping("/docs/proxy")
	public ExecuteResult tryItProxy(@RequestBody ExecuteCommand command) {
		return workspaceService.execute(command);
	}

	public record SyncBody(String collectionId, String mode, Boolean deleteStale) {
	}

	public record PortBody(Integer port) {
	}

	public record PromptBody(String prompt) {
	}

	public record QueryBody(String sql) {
	}

	public record PerformanceBody(String collectionId, String requestId, String environmentId, int virtualUsers, int iterations, int rampUpMs, String datasetId) {
	}

	public record ShareBody(ApiRequest request, ExecuteResult response) {
	}

	public record MqttBody(String broker, String topic, String username, String password, int qos) {
	}

	public record MqttPublishBody(String topic, String payload, int qos, boolean retained) {
	}

	public record SocketBody(String url, String event) {
	}

	public record SocketEmitBody(String event, String payload) {
	}

	public record ChaosBody(boolean enabled) {
	}

}
