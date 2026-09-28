package com.apiflow.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.ChangeResult;
import com.apiflow.model.Monitor;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.RunReport;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class LocalGapService {

	private final FileStore store;
	private final WorkspaceService workspaceService;
	private final MonitorService monitorService;
	private final AiService aiService;
	private final ObjectMapper mapper = new ObjectMapper();
	private final CopyOnWriteArrayList<CapturedCall> captured = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<ScheduledRun> schedules = new CopyOnWriteArrayList<>();
	private final AtomicBoolean proxyRunning = new AtomicBoolean();
	private CaptureProxy captureProxy;
	private int proxyPort;
	private Process standaloneProcess;

	public LocalGapService(FileStore store, WorkspaceService workspaceService, MonitorService monitorService, AiService aiService) {
		this.store = store;
		this.workspaceService = workspaceService;
		this.monitorService = monitorService;
		this.aiService = aiService;
		MockStateStore.bind(store);
		loadSchedules();
	}

	public synchronized int startCapture(int port) {
		stopCapture();
		try {
			captureProxy = new CaptureProxy(store.root().resolve("ca"), call -> {
				CapturedCall capturedCall = new CapturedCall();
				capturedCall.method = call.method();
				capturedCall.url = call.url();
				capturedCall.path = call.path();
				captured.add(0, capturedCall);
				while (captured.size() > 200) {
					captured.remove(captured.size() - 1);
				}
			});
			proxyPort = captureProxy.start(port);
			proxyRunning.set(true);
			return proxyPort;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not start capture proxy: " + ex.getMessage());
		}
	}

	public synchronized void stopCapture() {
		proxyRunning.set(false);
		if (captureProxy != null) {
			captureProxy.stop();
		}
	}

	public String captureCertificate() {
		return captureProxy == null ? store.root().resolve("ca").resolve("apiflow-ca.crt").toString() : captureProxy.certificatePath();
	}

	public List<CapturedCall> captured() {
		return List.copyOf(captured);
	}

	public ChangeResult importCaptured(String collectionId) {
		Workspace workspace = store.update(data -> {
			RequestCollection collection = data.getCollections().stream().filter(item -> item.getId().equals(collectionId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Collection not found"));
			for (CapturedCall call : captured) {
				ApiRequest request = new ApiRequest();
				request.setId(UUID.randomUUID().toString());
				request.setName(call.method + " " + call.path);
				request.setMethod(call.method);
				request.setUrl(call.url);
				collection.getRequests().add(request);
			}
		});
		return new ChangeResult(workspace, collectionId);
	}

	public ScheduledRun saveSchedule(ScheduledRun run) {
		if (run.id == null || run.id.isBlank()) {
			run.id = UUID.randomUUID().toString();
		}
		schedules.removeIf(item -> item.id.equals(run.id));
		schedules.add(run);
		persistSchedules();
		return run;
	}

	public List<ScheduledRun> schedules() {
		return List.copyOf(schedules);
	}

	@Scheduled(fixedDelay = 60_000)
	public void scheduledTick() {
		long now = System.currentTimeMillis();
		for (ScheduledRun run : schedules) {
			if (!run.enabled) {
				continue;
			}
			if (run.nextAt == 0 || now >= run.nextAt) {
				try {
					workspaceService.runCollection(run.collectionId, run.environmentId, "", "", false, "", false, 0, List.of());
				}
				catch (Exception ignored) {
					// keep the scheduler alive
				}
				run.nextAt = now + Math.max(run.intervalMs, 60_000);
				persistSchedules();
			}
		}
	}

	public Monitor claimPrivateRunner() {
		for (Monitor monitor : monitorService.list()) {
			if (monitor.isEnabled() && "private".equalsIgnoreCase(monitor.getRunnerMode())) {
				return monitorService.run(monitor.getId()) == null ? monitor : monitor;
			}
		}
		throw new IllegalArgumentException("No private monitor is waiting");
	}

	public String debugRun(RunReport report) {
		StringBuilder context = new StringBuilder();
		if (report != null) {
			for (var item : report.getItems()) {
				if (!item.isOk()) {
					context.append(item.getMethod()).append(' ').append(item.getName()).append(" status ").append(item.getStatus()).append(' ').append(item.getError()).append('\n');
					if (item.getResponseBody() != null && !item.getResponseBody().isBlank()) {
						context.append(item.getResponseBody(), 0, Math.min(500, item.getResponseBody().length())).append('\n');
					}
				}
			}
		}
		return aiService.chat(workspaceService.workspace().getSettings(), "Explain why this collection run failed and how to fix the requests or tests.", context.toString(), "debug");
	}

	public ObjectNode inferTypes(String example) {
		ObjectNode schema = mapper.createObjectNode();
		schema.put("type", "object");
		try {
			JsonNode node = mapper.readTree(example == null || example.isBlank() ? "{}" : example);
			schema.set("properties", properties(node));
		}
		catch (Exception ex) {
			schema.put("description", "Could not parse example");
		}
		return schema;
	}

	public String standaloneMock(String collectionId) {
		RequestCollection collection = workspaceService.exportCollection(collectionId);
		StringBuilder script = new StringBuilder();
		script.append("import http from 'node:http'\nconst routes = ");
		script.append(mapper.writeValueAsString(collection.getRequests().stream().map(request -> {
			var route = mapper.createObjectNode();
			route.put("method", request.getMethod());
			route.put("path", pathOf(request.getUrl()));
			route.put("status", request.getExtras() == null ? 200 : request.getExtras().getMockStatus());
			route.put("body", request.getExtras() == null ? "" : request.getExtras().getMockBody());
			return route;
		}).toList()));
		script.append("""
			
			http.createServer((req, res) => {
			  const route = routes.find((item) => item.method === req.method && req.url.startsWith(item.path))
			  if (!route) { res.writeHead(404); res.end('{}'); return }
			  res.writeHead(route.status || 200, { 'content-type': 'application/json' })
			  res.end(route.body || '{}')
			}).listen(process.env.PORT || 4010)
			console.log('Standalone mock on', process.env.PORT || 4010)
			""");
		try {
			var file = store.root().resolve("public").resolve("mock-" + collectionId + ".mjs");
			Files.createDirectories(file.getParent());
			java.nio.file.Files.writeString(file, script.toString(), StandardCharsets.UTF_8);
			ProcessBuilder builder = new ProcessBuilder("bash", "-lc", "nohup node \"" + file + "\" >> \"" + file.getParent().resolve("mock-" + collectionId + ".log") + "\" 2>&1 &");
			builder.redirectErrorStream(true);
			standaloneProcess = builder.start();
			standaloneProcess.getInputStream().readAllBytes();
			return file.toString();
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not write standalone mock");
		}
	}

	public ChangeResult forkCollection(String id) {
		RequestCollection source = workspaceService.exportCollection(id);
		source.setId(UUID.randomUUID().toString());
		source.setName(source.getName() + " fork");
		for (ApiRequest request : source.getRequests()) {
			request.setId(UUID.randomUUID().toString());
		}
		String focus = source.getId();
		Workspace workspace = store.update(data -> data.getCollections().add(source));
		return new ChangeResult(workspace, focus);
	}

	public RunReport simulate(String collectionId, String environmentId, List<String> failingPrefixes) {
		FaultInjector.arm(failingPrefixes);
		try {
			return workspaceService.runCollection(collectionId, environmentId, "", "", false, "", false, 0, List.of());
		}
		finally {
			FaultInjector.clear();
		}
	}

	public String grpcExample(String service, String method) {
		for (var collection : store.copy().getCollections()) {
			for (var request : collection.getRequests()) {
				String url = request.getUrl() == null ? "" : request.getUrl();
				boolean grpc = "GRPC".equalsIgnoreCase(request.getMethod()) || url.contains("grpc");
				if (grpc && url.contains(service) && url.contains(method)) {
					String example = request.getExtras() == null ? "" : request.getExtras().getMockBody();
					return example == null || example.isBlank() ? request.getBody() : example;
				}
			}
		}
		return "{\n  \"service\": \"" + service + "\",\n  \"method\": \"" + method + "\"\n}";
	}

	private ObjectNode properties(JsonNode node) {
		ObjectNode properties = mapper.createObjectNode();
		if (node != null && node.isObject()) {
			node.properties().forEach(entry -> {
				ObjectNode property = mapper.createObjectNode();
				JsonNode value = entry.getValue();
				property.put("type", value.isNumber() ? "number" : value.isBoolean() ? "boolean" : value.isArray() ? "array" : value.isObject() ? "object" : "string");
				properties.set(entry.getKey(), property);
			});
		}
		return properties;
	}

	public ChangeResult applyInferredModel(String collectionId, String example) {
		ObjectNode schema = inferTypes(example);
		Workspace workspace = store.update(data -> {
			RequestCollection collection = data.getCollections().stream().filter(item -> item.getId().equals(collectionId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Collection not found"));
			collection.setBodySchema(schema.toString());
			collection.getTypedParams().clear();
			if (schema.get("properties") != null) {
				schema.get("properties").properties().forEach(entry -> {
					com.apiflow.model.TypedField field = new com.apiflow.model.TypedField();
					field.setKey(entry.getKey());
					field.setDataType(entry.getValue().path("type").asString("string"));
					field.setRequired(true);
					collection.getTypedParams().add(field);
				});
			}
		});
		ActivityLog.add(store, "spec", "Created a request model from an example");
		return new ChangeResult(workspace, collectionId);
	}

	public ChangeResult openPullRequest(String sourceId, String targetId, String title) {
		com.apiflow.model.CollectionPullRequest request = new com.apiflow.model.CollectionPullRequest();
		request.setId(UUID.randomUUID().toString());
		request.setSourceCollectionId(sourceId);
		request.setTargetCollectionId(targetId);
		request.setTitle(title == null || title.isBlank() ? "Collection changes" : title);
		request.setStatus("open");
		request.setCreatedAt(System.currentTimeMillis());
		Workspace workspace = store.update(data -> data.getPullRequests().add(0, request));
		ActivityLog.add(store, "pull-request", "Opened collection pull request " + request.getTitle());
		return new ChangeResult(workspace, request.getId());
	}

	public ChangeResult mergePullRequest(String id) {
		com.apiflow.model.CollectionPullRequest request = store.copy().getPullRequests().stream().filter(item -> item.getId().equals(id)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Pull request not found"));
		RequestCollection source = workspaceService.exportCollection(request.getSourceCollectionId());
		Workspace workspace = store.update(data -> {
			RequestCollection target = data.getCollections().stream().filter(item -> item.getId().equals(request.getTargetCollectionId())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Target collection not found"));
			for (ApiRequest incoming : source.getRequests()) {
				ApiRequest existing = target.getRequests().stream()
					.filter(item -> item.getName().equals(incoming.getName()) && item.getMethod().equalsIgnoreCase(incoming.getMethod()))
					.findFirst().orElse(null);
				if (existing == null) {
					incoming.setId(UUID.randomUUID().toString());
					target.getRequests().add(incoming);
				}
				else {
					existing.setUrl(incoming.getUrl());
					existing.setMethod(incoming.getMethod());
					existing.setHeaders(incoming.getHeaders());
					existing.setBody(incoming.getBody());
					existing.setBodyType(incoming.getBodyType());
				}
			}
			data.getPullRequests().stream().filter(item -> item.getId().equals(id)).findFirst().ifPresent(item -> item.setStatus("merged"));
		});
		ActivityLog.add(store, "pull-request", "Merged collection pull request " + request.getTitle());
		return new ChangeResult(workspace, id);
	}

	public int startGrpcMock(int port) {
		return GrpcMockServer.start(store.copy().getCollections(), port <= 0 ? 50051 : port);
	}

	private void loadSchedules() {
		try {
			var file = store.root().resolve("schedules.json");
			if (!Files.exists(file)) {
				return;
			}
			ScheduledRun[] loaded = mapper.readValue(Files.readString(file), ScheduledRun[].class);
			schedules.clear();
			if (loaded != null) {
				schedules.addAll(List.of(loaded));
			}
		}
		catch (Exception ignored) {
			schedules.clear();
		}
	}

	private void persistSchedules() {
		try {
			var file = store.root().resolve("schedules.json");
			Files.createDirectories(file.getParent());
			Files.writeString(file, mapper.writeValueAsString(new java.util.ArrayList<>(schedules)));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not save schedules");
		}
	}

	private static String pathOf(String url) {
		if (url == null) {
			return "/";
		}
		int scheme = url.indexOf("://");
		String value = url;
		if (scheme >= 0) {
			int slash = url.indexOf('/', scheme + 3);
			value = slash < 0 ? "/" : url.substring(slash);
		}
		int query = value.indexOf('?');
		return query < 0 ? value : value.substring(0, query);
	}

	public static class CapturedCall {
		public String method = "GET";
		public String url = "";
		public String path = "/";
	}

	public static class ScheduledRun {
		public String id;
		public String collectionId = "";
		public String environmentId = "";
		public long intervalMs = 300_000;
		public long nextAt;
		public boolean enabled = true;
	}

}
