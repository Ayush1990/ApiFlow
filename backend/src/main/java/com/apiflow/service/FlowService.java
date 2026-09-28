package com.apiflow.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.apiflow.model.ChangeResult;
import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.FlowDefinition;
import com.apiflow.model.FlowDefinition.FlowBlock;
import com.apiflow.model.FlowDefinition.FlowConnection;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

@Service
public class FlowService {

	private final FileStore store;
	private final WorkspaceService workspaceService;
	private final AiService aiService;
	private final Map<String, DeployedFlow> deployed = new ConcurrentHashMap<>();
	private final Map<String, List<FlowRunResult>> history = new ConcurrentHashMap<>();
	private final ObjectMapper mapper = new ObjectMapper();

	public FlowService(FileStore store, WorkspaceService workspaceService, AiService aiService) {
		this.store = store;
		this.workspaceService = workspaceService;
		this.aiService = aiService;
	}

	public List<FlowDefinition> list() {
		return store.copy().getFlows();
	}

	public FlowDefinition get(String id) {
		return store.copy().getFlows().stream().filter(item -> item.getId().equals(id)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Flow not found"));
	}

	public ChangeResult save(FlowDefinition flow) {
		if (flow.getId() == null || flow.getId().isBlank()) {
			flow.setId(UUID.randomUUID().toString());
		}
		String id = flow.getId();
		Workspace workspace = store.update(data -> {
			data.getFlows().removeIf(item -> item.getId().equals(id));
			data.getFlows().add(flow);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult delete(String id) {
		undeploy(id);
		Workspace workspace = store.update(data -> data.getFlows().removeIf(item -> item.getId().equals(id)));
		return new ChangeResult(workspace, "");
	}

	public FlowRunResult run(String id, Map<String, String> input) {
		FlowDefinition flow = get(id);
		Map<String, String> context = new LinkedHashMap<>(input == null ? Map.of() : input);
		Map<String, FlowBlock> blocks = new HashMap<>();
		for (FlowBlock block : flow.getBlocks()) {
			blocks.put(block.getId(), block);
		}
		List<FlowStepResult> steps = new ArrayList<>();
		String current = flow.getBlocks().stream().filter(item -> "start".equals(item.getType())).map(FlowBlock::getId).findFirst()
			.orElse(flow.getBlocks().isEmpty() ? null : flow.getBlocks().get(0).getId());
		int guard = 0;
		while (current != null && guard++ < 100) {
			FlowBlock block = blocks.get(current);
			if (block == null) {
				break;
			}
			FlowStepResult step = executeBlock(block, context);
			steps.add(step);
			if ("loop".equals(block.getType())) {
				String bodyId = nextBlock(flow, block, true);
				FlowBlock body = blocks.get(bodyId);
				int times = Integer.parseInt(context.getOrDefault("loopTimes", "1"));
				for (int index = 0; index < times && body != null && guard++ < 100; index++) {
					FlowStepResult inner = executeBlock(body, context);
					steps.add(inner);
					if (!inner.ok()) {
						current = nextBlock(flow, body, false);
						body = null;
					}
				}
			if (body != null) {
				current = nextBlock(flow, body, true);
			}
			continue;
		}
		current = nextBlock(flow, block, step.ok());
		}
		FlowRunResult result = new FlowRunResult(steps, context);
		history.computeIfAbsent(id, key -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(0, result);
		List<FlowRunResult> saved = history.get(id);
		while (saved.size() > 20) {
			saved.remove(saved.size() - 1);
		}
		return result;
	}

	public List<FlowRunResult> history(String id) {
		return List.copyOf(history.getOrDefault(id, List.of()));
	}

	public FlowDefinition generateFromPrompt(String prompt) {
		String suggestion = aiService.suggestFlowBlocks(prompt);
		FlowDefinition flow = new FlowDefinition();
		flow.setId(UUID.randomUUID().toString());
		flow.setName("AI Flow");
		flow.setDescription(prompt == null ? "" : prompt);
		try {
			FlowDefinition generated = mapper.readValue(suggestion, FlowDefinition.class);
			flow.setBlocks(generated.getBlocks());
			flow.setConnections(generated.getConnections());
			if (generated.getName() != null && !generated.getName().isBlank()) {
				flow.setName(generated.getName());
			}
		}
		catch (Exception ex) {
			FlowBlock start = block("start", "Start", "start", 40, 40, "{}");
			FlowBlock request = block("req1", "HTTP Request", "request", 240, 40,
				"{\"collectionId\":\"\",\"requestId\":\"\",\"environmentId\":\"\"}");
			flow.getBlocks().add(start);
			flow.getBlocks().add(request);
			FlowConnection connection = new FlowConnection();
			connection.setFrom("start");
			connection.setTo("req1");
			flow.getConnections().add(connection);
		}
		save(flow);
		return flow;
	}

	public DeployResult deploy(String id, int port) {
		undeploy(id);
		FlowDefinition flow = get(id);
		int chosen = port <= 0 ? 4020 : port;
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", chosen), 0);
			int actualPort = server.getAddress().getPort();
			server.createContext("/flow/" + id, exchange -> {
				if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
					exchange.sendResponseHeaders(405, -1);
					exchange.close();
					return;
				}
				String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				Map<String, String> input = parseInput(body);
				FlowRunResult result = run(id, input);
				byte[] bytes = mapper.writeValueAsBytes(result);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
				exchange.sendResponseHeaders(200, bytes.length);
				try (OutputStream stream = exchange.getResponseBody()) {
					stream.write(bytes);
				}
			});
			server.start();
			deployed.put(id, new DeployedFlow(server, actualPort));
			flow.setDeployed(true);
			flow.setDeployPort(actualPort);
			save(flow);
			return new DeployResult(true, actualPort, "http://127.0.0.1:" + actualPort + "/flow/" + id, publicUrl(actualPort, id));
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not deploy flow: " + ex.getMessage());
		}
	}

	public void undeploy(String id) {
		DeployedFlow running = deployed.remove(id);
		if (running != null) {
			running.server().stop(0);
		}
		FlowDefinition flow = store.copy().getFlows().stream().filter(item -> item.getId().equals(id)).findFirst().orElse(null);
		if (flow != null) {
			flow.setDeployed(false);
			flow.setDeployPort(0);
			save(flow);
		}
	}

	private String nextBlock(FlowDefinition flow, FlowBlock block, boolean ok) {
		List<FlowConnection> outgoing = new ArrayList<>();
		for (FlowConnection connection : flow.getConnections()) {
			if (block.getId().equals(connection.getFrom())) {
				outgoing.add(connection);
			}
		}
		if (outgoing.isEmpty()) {
			return null;
		}
		if (!ok) {
			for (FlowConnection connection : outgoing) {
				if ("error".equalsIgnoreCase(connection.getWhen())) {
					return connection.getTo();
				}
			}
		}
		if ("condition".equals(block.getType())) {
			String want = ok ? "true" : "false";
			for (FlowConnection connection : outgoing) {
				if (want.equalsIgnoreCase(connection.getWhen())) {
					return connection.getTo();
				}
			}
		}
		if ("loop".equals(block.getType())) {
			return outgoing.get(0).getTo();
		}
		return outgoing.get(0).getTo();
	}

	private List<FlowBlock> orderedBlocks(FlowDefinition flow) {
		Map<String, FlowBlock> blocks = new HashMap<>();
		for (FlowBlock block : flow.getBlocks()) {
			blocks.put(block.getId(), block);
		}
		List<FlowBlock> ordered = new ArrayList<>();
		String current = flow.getBlocks().stream().filter(item -> "start".equals(item.getType())).map(FlowBlock::getId).findFirst()
			.orElse(flow.getBlocks().isEmpty() ? null : flow.getBlocks().get(0).getId());
		int guard = 0;
		while (current != null && guard++ < 100) {
			FlowBlock block = blocks.get(current);
			if (block != null) {
				ordered.add(block);
			}
			String next = null;
			for (FlowConnection connection : flow.getConnections()) {
				if (current.equals(connection.getFrom())) {
					next = connection.getTo();
					break;
				}
			}
			current = next;
		}
		if (ordered.isEmpty()) {
			ordered.addAll(flow.getBlocks());
		}
		return ordered;
	}

	private FlowStepResult executeBlock(FlowBlock block, Map<String, String> context) {
		try {
			Map<String, Object> config = mapper.readValue(block.getConfig(), Map.class);
			return switch (block.getType() == null ? "" : block.getType()) {
				case "delay" -> {
					int ms = config.get("ms") instanceof Number number ? number.intValue() : 0;
					if (ms > 0) {
						Thread.sleep(ms);
					}
					yield new FlowStepResult(block.getId(), block.getType(), "waited " + ms + "ms", true);
				}
				case "request" -> {
					String collectionId = String.valueOf(config.getOrDefault("collectionId", ""));
					String requestId = String.valueOf(config.getOrDefault("requestId", ""));
					String environmentId = String.valueOf(config.getOrDefault("environmentId", ""));
					ExecuteResult result = workspaceService.executeRequest(collectionId, requestId, environmentId, "", new LinkedHashMap<>(context));
					context.put("lastStatus", String.valueOf(result.getStatus()));
					context.put("lastBody", result.getBody() == null ? "" : result.getBody());
					yield new FlowStepResult(block.getId(), block.getType(), result.getStatus() + " " + result.getStatusText(), result.getStatus() < 400);
				}
				case "transform" -> {
					String mode = String.valueOf(config.getOrDefault("mode", "fql"));
					String script = String.valueOf(config.getOrDefault("script", ""));
					String detail = FlowTransformHelper.apply(mode, script, context);
					yield new FlowStepResult(block.getId(), block.getType(), detail, true);
				}
				case "condition" -> {
					String expression = String.valueOf(config.getOrDefault("expression", "true"));
					boolean ok = evaluateCondition(expression, context);
					yield new FlowStepResult(block.getId(), block.getType(), expression + " => " + ok, ok);
				}
				case "loop" -> {
					int times = config.get("times") instanceof Number number ? number.intValue() : 1;
					context.put("loopTimes", String.valueOf(Math.max(times, 1)));
					yield new FlowStepResult(block.getId(), block.getType(), "loop " + times, true);
				}
				case "flow" -> {
					String flowId = String.valueOf(config.getOrDefault("flowId", ""));
					if (flowId.isBlank()) {
						yield new FlowStepResult(block.getId(), block.getType(), "missing flowId", false);
					}
					FlowRunResult nested = run(flowId, context);
					yield new FlowStepResult(block.getId(), block.getType(), nested.steps().size() + " nested steps", nested.steps().stream().allMatch(FlowStepResult::ok));
				}
				default -> new FlowStepResult(block.getId(), block.getType(), "ok", true);
			};
		}
		catch (Exception ex) {
			return new FlowStepResult(block.getId(), block.getType(), ex.getMessage(), false);
		}
	}

	private boolean evaluateCondition(String expression, Map<String, String> context) {
		String value = expression == null ? "" : expression;
		for (Map.Entry<String, String> entry : context.entrySet()) {
			value = value.replace("{{" + entry.getKey() + "}}", entry.getValue());
		}
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(-?\\d+)\\s*(<=|>=|==|!=|<|>)\\s*(-?\\d+)").matcher(value.trim());
		if (matcher.find()) {
			long left = Long.parseLong(matcher.group(1));
			long right = Long.parseLong(matcher.group(3));
			return switch (matcher.group(2)) {
				case "<" -> left < right;
				case ">" -> left > right;
				case "<=" -> left <= right;
				case ">=" -> left >= right;
				case "==" -> left == right;
				case "!=" -> left != right;
				default -> false;
			};
		}
		String lower = value.toLowerCase();
		return !lower.contains("false") && !value.contains("0");
	}

	private Map<String, String> parseInput(String body) {
		try {
			Map<?, ?> parsed = mapper.readValue(body == null || body.isBlank() ? "{}" : body, Map.class);
			Map<String, String> result = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : parsed.entrySet()) {
				result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
			}
			return result;
		}
		catch (Exception ex) {
			return Map.of();
		}
	}

	private static FlowBlock block(String id, String label, String type, double x, double y, String config) {
		FlowBlock block = new FlowBlock();
		block.setId(id);
		block.setLabel(label);
		block.setType(type);
		block.setX(x);
		block.setY(y);
		block.setConfig(config);
		return block;
	}

	private String publicUrl(int port, String id) {
		return "http://0.0.0.0:" + port + "/flow/" + id;
	}

	record DeployedFlow(HttpServer server, int port) {
	}

	public record FlowStepResult(String blockId, String type, String detail, boolean ok) {
	}

	public record FlowRunResult(List<FlowStepResult> steps, Map<String, String> context) {
	}

	public record DeployResult(boolean ok, int port, String localUrl, String publicUrl) {
	}

}
