package com.apiflow.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import org.springframework.stereotype.Component;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.MockScenario;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.RequestExtras;
import com.sun.net.httpserver.HttpServer;

@Component
public class MockServer {

	private final DatasetService datasetService;
	private final Map<String, Running> servers = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<MockLogEntry> log = new CopyOnWriteArrayList<>();
	private final Map<String, Integer> scenarioCounters = new ConcurrentHashMap<>();
	private volatile boolean chaosMode;

	public MockServer(DatasetService datasetService) {
		this.datasetService = datasetService;
	}

	public synchronized MockState state() {
		if (servers.isEmpty()) {
			return new MockState(false, 0, List.of(), "", chaosMode);
		}
		Running first = servers.values().iterator().next();
		return new MockState(true, first.port, List.copyOf(servers.keySet()), publicUrl(first.port), chaosMode);
	}

	public synchronized MockState setChaosMode(boolean enabled) {
		this.chaosMode = enabled;
		return state();
	}

	public List<MockLogEntry> log() {
		return List.copyOf(log);
	}

	public synchronized MockState start(RequestCollection collection, int requestedPort) {
		return start(collection.getId(), collection, requestedPort);
	}

	public synchronized MockState start(String key, RequestCollection collection, int requestedPort) {
		return start(key, collection, requestedPort, false);
	}

	public synchronized MockState start(String key, RequestCollection collection, int requestedPort, boolean publicBind) {
		stop(key);
		int chosen = requestedPort <= 0 ? 4010 : requestedPort;
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(publicBind ? "0.0.0.0" : "127.0.0.1", chosen), 0);
			int port = server.getAddress().getPort();
			List<Route> routes = routes(collection);
			routes.addAll(scenarioRoutes(collection));
			server.createContext("/", exchange -> handle(exchange, routes, collection));
			server.start();
			servers.put(key, new Running(server, port, collection.getName(), collection));
			return state();
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not start the mock server on port " + chosen + ".");
		}
	}

	public synchronized MockState startManual(String key, int requestedPort, List<Route> routes) {
		stop(key);
		int chosen = requestedPort <= 0 ? 4010 : requestedPort;
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", chosen), 0);
			int port = server.getAddress().getPort();
			server.createContext("/", exchange -> handle(exchange, routes == null ? List.of() : routes, null));
			server.start();
			servers.put(key, new Running(server, port, "Manual mock", null));
			return state();
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not start manual mock server on port " + chosen + ".");
		}
	}

	public synchronized MockState startFromExamples(String key, RequestCollection collection, int requestedPort) {
		return startManual(key + ":examples", requestedPort, exampleRoutes(collection));
	}

	public synchronized MockState startOpenApi(String key, String content, int requestedPort) {
		stop(key);
		int chosen = requestedPort <= 0 ? 4010 : requestedPort;
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", chosen), 0);
			int port = server.getAddress().getPort();
			List<Route> routes = OpenApiMock.routes(content);
			server.createContext("/", exchange -> handle(exchange, routes, null));
			server.start();
			servers.put(key, new Running(server, port, "OpenAPI mock", null));
			return state();
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not start the OpenAPI mock server on port " + chosen + ".");
		}
	}

	public synchronized MockState stop() {
		for (String key : List.copyOf(servers.keySet())) {
			stop(key);
		}
		return state();
	}

	public synchronized MockState stop(String key) {
		Running running = servers.remove(key);
		if (running != null) {
			running.server.stop(0);
		}
		return state();
	}

	public synchronized MockState resync(String key, RequestCollection collection) {
		Running running = servers.get(key);
		if (running == null) {
			throw new IllegalArgumentException("No mock server running for " + key);
		}
		return start(key, collection, running.port);
	}

	private void handle(com.sun.net.httpserver.HttpExchange exchange, List<Route> routes, RequestCollection collection) throws IOException {
		if (chaosMode && Math.random() < 0.08) {
			if (Math.random() < 0.5) {
				try {
					Thread.sleep(500 + (long) (Math.random() * 2000));
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			else {
				byte[] body = "{\"error\":\"chaos simulated failure\"}".getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(503, body.length);
				try (OutputStream stream = exchange.getResponseBody()) {
					stream.write(body);
				}
				return;
			}
		}
		if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
			exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
			exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
			exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
			return;
		}
		String path = exchange.getRequestURI().getPath();
		String rawQuery = exchange.getRequestURI().getRawQuery();
		String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		Route route = null;
		int score = -1;
		for (Route candidate : routes) {
			if (!candidate.method.equalsIgnoreCase(exchange.getRequestMethod())) {
				continue;
			}
			int pathScore = matchPath(candidate.path, path);
			if (pathScore < 0) {
				continue;
			}
			int candidateScore = pathScore + matchScore(candidate.query, rawQuery);
			if (candidateScore < 0) {
				continue;
			}
			if (!candidate.bodyMatch.isBlank() && !requestBody.contains(candidate.bodyMatch)) {
				continue;
			}
			int headerScore = headerScore(candidate.headerMatch, exchange.getRequestHeaders());
			if (headerScore < 0) {
				continue;
			}
			candidateScore += headerScore;
			if (!candidate.bodyMatch.isBlank()) {
				candidateScore += 30;
			}
			if (candidateScore > score) {
				route = candidate;
				score = candidateScore;
			}
		}
		log.add(new MockLogEntry(exchange.getRequestMethod(), path, route != null, requestBody.length() > 120 ? requestBody.substring(0, 120) + "…" : requestBody));
		if (route != null && route.delayMs > 0) {
			try {
				Thread.sleep(route.delayMs);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		String responseBody = route == null ? "{\"error\":\"No mock for this path\"}" : resolveBody(route, path, requestBody);
		int status = route == null ? 404 : route.status;
		String contentType = route == null ? "application/json" : route.contentType;
		if (collection != null && collection.getMockScript() != null && !collection.getMockScript().isBlank()) {
			MockOverride override = runMockScript(collection.getMockScript(), exchange.getRequestMethod(), path, requestBody, status, responseBody, contentType);
			status = override.status;
			responseBody = override.body;
			contentType = override.contentType;
		}
		byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", contentType);
		exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream stream = exchange.getResponseBody()) {
			stream.write(body);
		}
	}

	private static MockOverride runMockScript(String script, String method, String path, String requestBody, int status, String body, String contentType) {
		String key = method + " " + path;
		String stateJson = MockStateStore.get(key);
		try (Context context = Context.newBuilder("js").build()) {
			context.getBindings("js").putMember("method", method);
			context.getBindings("js").putMember("path", path);
			context.getBindings("js").putMember("requestBody", requestBody == null ? "" : requestBody);
			Value result = context.eval("js", """
				const state = %s;
				const mock = { status: %d, body: %s, contentType: %s, method, path, requestBody };
				%s
				({ mock, stateJson: JSON.stringify(state) });
				""".formatted(stateJson == null || stateJson.isBlank() ? "{}" : stateJson, status, jsString(body), jsString(contentType), script));
			Value mock = result.getMember("mock");
			String nextState = result.getMember("stateJson").isNull() ? "{}" : result.getMember("stateJson").asString();
			MockStateStore.put(key, nextState);
			int nextStatus = mock.getMember("status").isNumber() ? mock.getMember("status").asInt() : status;
			String nextBody = mock.getMember("body").isNull() ? "" : mock.getMember("body").asString();
			String nextType = mock.getMember("contentType").isNull() ? contentType : mock.getMember("contentType").asString();
			return new MockOverride(nextStatus, nextBody, nextType);
		}
		catch (Exception ex) {
			return new MockOverride(500, "{\"error\":\"" + ex.getMessage().replace("\"", "'") + "\"}", "application/json");
		}
	}

	private static String jsString(String value) {
		if (value == null) {
			return "\"\"";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
	}

	private record MockOverride(int status, String body, String contentType) {
	}

	static String template(String body, String path, String requestBody) {
		if (body == null) {
			return "";
		}
		String current = body;
		current = current.replace("{{path}}", path);
		current = current.replace("{{requestBody}}", requestBody == null ? "" : requestBody);
		String[] parts = path.split("/");
		for (int index = 0; index < parts.length; index++) {
			current = current.replace("{{pathSegment" + index + "}}", parts[index]);
		}
		return current;
	}

	static int matchPath(String pattern, String path) {
		if (pattern == null || pattern.isBlank()) {
			return -1;
		}
		if (pattern.equals(path)) {
			return 100;
		}
		if (pattern.endsWith("*")) {
			String prefix = pattern.substring(0, pattern.length() - 1);
			if (path.startsWith(prefix)) {
				return 50 + prefix.length();
			}
		}
		String regex = "^" + Pattern.quote(pattern).replace("\\*", ".*") + "$";
		if (pattern.contains("*")) {
			regex = "^" + pattern.replace("*", "[^/]+").replace("/", "\\/") + "$";
		}
		if (Pattern.compile(regex).matcher(path).matches()) {
			return 40;
		}
		return -1;
	}

	static List<Route> exampleRoutes(RequestCollection collection) {
		List<Route> routes = new ArrayList<>();
		if (collection == null) {
			return routes;
		}
		for (ApiRequest request : collection.getRequests()) {
			String path = pathOf(request.getUrl());
			if (path.isBlank()) {
				continue;
			}
			if (!request.getExamples().isEmpty()) {
				for (var example : request.getExamples()) {
					routes.add(new Route(request.getMethod() == null ? "GET" : request.getMethod(), path, queryOf(request.getUrl()),
						example.getStatus() <= 0 ? 200 : example.getStatus(),
						example.getContentType() == null || example.getContentType().isBlank() ? "application/json" : example.getContentType(),
						example.getBody() == null ? "{}" : example.getBody(),
						example.getRequestBody(), 0, path.contains("*"), "", List.of(), "", -1, example.getRequestHeaders()));
				}
			}
			else if (request.getExampleBody() != null && !request.getExampleBody().isBlank()) {
				routes.add(new Route(request.getMethod() == null ? "GET" : request.getMethod(), path, queryOf(request.getUrl()), 200,
					request.getExampleContentType() == null || request.getExampleContentType().isBlank() ? "application/json" : request.getExampleContentType(),
					request.getExampleBody(), "", 0, path.contains("*")));
			}
		}
		return routes;
	}

	private String resolveBody(Route route, String path, String requestBody) {
		if (route.scenarioKey != null && !route.scenarioKey.isBlank()) {
			int index = scenarioCounters.merge(route.scenarioKey, 1, Integer::sum) - 1;
			if (route.scenarioSteps != null && !route.scenarioSteps.isEmpty()) {
				MockScenario.MockScenarioStep step = route.scenarioSteps.get(Math.min(index, route.scenarioSteps.size() - 1));
				if (step.getDelayMs() > 0) {
					try {
						Thread.sleep(step.getDelayMs());
					}
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
				if (step.getDatasetId() != null && !step.getDatasetId().isBlank()) {
					return datasetService.bodyFromRow(step.getDatasetId(), step.getDatasetRow() < 0 ? index : step.getDatasetRow(), step.getBody());
				}
				return template(step.getBody(), path, requestBody);
			}
		}
		if (route.datasetId != null && !route.datasetId.isBlank()) {
			return datasetService.bodyFromRow(route.datasetId, route.datasetRow, route.body);
		}
		return template(route.body, path, requestBody);
	}

	static List<Route> scenarioRoutes(RequestCollection collection) {
		List<Route> routes = new ArrayList<>();
		if (collection == null) {
			return routes;
		}
		for (MockScenario scenario : collection.getMockScenarios()) {
			if (scenario.getPath() == null || scenario.getPath().isBlank() || scenario.getSteps().isEmpty()) {
				continue;
			}
			MockScenario.MockScenarioStep first = scenario.getSteps().get(0);
			routes.add(new Route(scenario.getMethod(), scenario.getPath(), "", first.getStatus(), first.getContentType(), first.getBody(), "",
				first.getDelayMs(), scenario.getPath().contains("*"), scenario.getId(), scenario.getSteps(), first.getDatasetId(), first.getDatasetRow(), ""));
		}
		return routes;
	}

	static List<Route> routes(RequestCollection collection) {
		List<Route> routes = new ArrayList<>(exampleRoutes(collection));
		if (collection == null) {
			return routes;
		}
		for (ApiRequest request : collection.getRequests()) {
			String path = pathOf(request.getUrl());
			if (path.isBlank()) {
				continue;
			}
			RequestExtras extras = request.getExtras();
			String body = extras.isMockEnabled() && !extras.getMockBody().isBlank() ? extras.getMockBody() : request.getExampleBody();
			if (body == null || body.isBlank()) {
				body = "{\"mocked\":true}";
			}
			int status = extras.isMockEnabled() ? extras.getMockStatus() : 200;
			String type = extras.isMockEnabled() ? extras.getMockContentType() : request.getExampleContentType();
			if (type == null || type.isBlank()) {
				type = "application/json";
			}
			boolean wildcard = path.contains("*");
			routes.add(new Route(request.getMethod() == null ? "GET" : request.getMethod(), path, queryOf(request.getUrl()), status, type, body, extras.getMockBodyMatch(), extras.getMockDelayMs(), wildcard));
		}
		return routes;
	}

	static int matchScore(String expected, String rawQuery) {
		if (expected == null || expected.isBlank()) {
			return 0;
		}
		Map<String, String> incoming = queryMap(rawQuery);
		for (Map.Entry<String, String> entry : queryMap(expected).entrySet()) {
			if (!entry.getValue().equals(incoming.get(entry.getKey()))) {
				return -1;
			}
		}
		return queryMap(expected).size();
	}

	static int headerScore(String expected, com.sun.net.httpserver.Headers incoming) {
		if (expected == null || expected.isBlank()) {
			return 0;
		}
		int score = 0;
		for (String line : expected.split("\\R")) {
			int colon = line.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String name = line.substring(0, colon).trim();
			String value = line.substring(colon + 1).trim();
			String actual = incoming == null ? null : incoming.getFirst(name);
			if (actual == null || !actual.equals(value)) {
				return -1;
			}
			score += 20;
		}
		return score;
	}

	static String pathOf(String url) {
		if (url == null) {
			return "";
		}
		String value = url.trim().replaceAll("\\{\\{\\s*[A-Za-z0-9_.-]+\\s*}}", "");
		if (value.startsWith("http://") || value.startsWith("https://")) {
			try {
				String path = URI.create(value).getPath();
				return path == null || path.isBlank() ? "/" : path;
			}
			catch (Exception ex) {
				return "";
			}
		}
		int query = value.indexOf('?');
		if (query >= 0) {
			value = value.substring(0, query);
		}
		if (value.isBlank()) {
			return "";
		}
		return value.startsWith("/") ? value : "/" + value;
	}

	static String queryOf(String url) {
		if (url == null) {
			return "";
		}
		String value = url.trim().replaceAll("\\{\\{\\s*[A-Za-z0-9_.-]+\\s*}}", "");
		String raw = "";
		if (value.startsWith("http://") || value.startsWith("https://")) {
			try {
				raw = URI.create(value).getRawQuery();
			}
			catch (Exception ex) {
				return "";
			}
		}
		else {
			int query = value.indexOf('?');
			raw = query >= 0 ? value.substring(query + 1) : "";
		}
		return canonical(raw);
	}

	private static String canonical(String raw) {
		Map<String, String> map = queryMap(raw);
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, String> entry : map.entrySet()) {
			if (builder.length() > 0) {
				builder.append('&');
			}
			builder.append(entry.getKey()).append('=').append(entry.getValue());
		}
		return builder.toString();
	}

	private static Map<String, String> queryMap(String raw) {
		Map<String, String> map = new TreeMap<>();
		if (raw == null || raw.isBlank()) {
			return map;
		}
		for (String part : raw.split("&")) {
			if (part.isBlank()) {
				continue;
			}
			int equals = part.indexOf('=');
			String key = decode(equals < 0 ? part : part.substring(0, equals));
			String value = decode(equals < 0 ? "" : part.substring(equals + 1));
			map.put(key, value);
		}
		return map;
	}

	private static String decode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		}
		catch (Exception ex) {
			return value;
		}
	}

	public record Route(String method, String path, String query, int status, String contentType, String body, String bodyMatch, int delayMs, boolean wildcard,
		String scenarioKey, List<MockScenario.MockScenarioStep> scenarioSteps, String datasetId, int datasetRow, String headerMatch) {

		public Route(String method, String path, String query, int status, String contentType, String body, String bodyMatch, int delayMs, boolean wildcard) {
			this(method, path, query, status, contentType, body, bodyMatch, delayMs, wildcard, "", List.of(), "", -1, "");
		}

		public static Route manual(String method, String path, String query, int status, String contentType, String body, String bodyMatch, int delayMs, boolean wildcard) {
			return new Route(method, path, query, status, contentType, body, bodyMatch, delayMs, wildcard);
		}
	}

	record Running(HttpServer server, int port, String name, RequestCollection collection) {
	}

	public record MockState(boolean running, int port, List<String> activeKeys, String publicUrl, boolean chaosMode) {
	}

	private static String publicUrl(int port) {
		return "http://127.0.0.1:" + port;
	}

	public record MockLogEntry(String method, String path, boolean matched, String bodyPreview) {
	}

}
