package com.apiflow.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.FilePart;
import com.apiflow.model.KeyValue;
import com.apiflow.model.StoredCookie;
import com.apiflow.model.TimelineEntry;
import com.apiflow.store.FileStore;

@Component
public class RequestExecutor {

	private final FileStore store;

	public RequestExecutor(FileStore store) {
		this.store = store;
	}

	private String scriptMode() {
		var settings = store.copy().getSettings();
		return settings == null ? "safe" : settings.getScriptMode();
	}

	private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");
	private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "WS", "SSE", "GRPC", "SOAP");
	private static final int MAX_BODY_BYTES = 1_000_000;

	public ExecuteResult execute(ExecuteCommand command, Map<String, String> variables, List<StoredCookie> jar) {
		try {
			return send(command, variables, jar);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return ExecuteResult.failure("Request was interrupted");
		}
		catch (Exception ex) {
			String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			return ExecuteResult.failure(message);
		}
	}

	public static String interpolate(String input, Map<String, String> variables) {
		String current = input == null ? "" : input;
		if (!current.isEmpty() && variables != null && !variables.isEmpty()) {
			for (int pass = 0; pass < 3; pass++) {
				Matcher matcher = VARIABLE.matcher(current);
				StringBuffer out = new StringBuffer();
				boolean found = false;
				while (matcher.find()) {
					found = true;
					String replacement = variables.getOrDefault(matcher.group(1), matcher.group(0));
					matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
				}
				if (!found) {
					break;
				}
				matcher.appendTail(out);
				current = out.toString();
			}
		}
		return VariableResolver.expandDynamic(current, variables);
	}

	private HttpClient httpClient(ExecuteCommand command) {
		HttpClient.Builder builder = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(command.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
		String httpVersion = command.getExtras() == null ? "" : command.getExtras().getHttpVersion();
		if ("h2".equalsIgnoreCase(httpVersion) || "http/2".equalsIgnoreCase(httpVersion) || "http2".equalsIgnoreCase(httpVersion)) {
			builder.version(HttpClient.Version.HTTP_2);
		}
		else {
			builder.version(HttpClient.Version.HTTP_1_1);
		}
		javax.net.ssl.SSLContext context = ClientCerts.context(command.getExtras());
		if (context != null) {
			builder.sslContext(context);
		}
		String proxy = command.getExtras().getProxyUrl();
		String bypass = "";
		var settings = store.copy().getSettings();
		if (settings != null) {
			if (proxy == null || proxy.isBlank()) {
				proxy = settings.getProxyUrl();
			}
			bypass = settings.getProxyBypass();
		}
		if (proxy != null && !proxy.isBlank()) {
			try {
				java.net.URI proxyUri = java.net.URI.create(proxy);
				java.net.InetSocketAddress proxyAddress = new java.net.InetSocketAddress(
					proxyUri.getHost(),
					proxyUri.getPort() > 0 ? proxyUri.getPort() : 8080);
				String bypassList = bypass == null ? "" : bypass;
				builder.proxy(new java.net.ProxySelector() {
					@Override
					public java.util.List<java.net.Proxy> select(java.net.URI uri) {
						if (shouldBypass(uri == null ? "" : uri.getHost(), bypassList)) {
							return java.util.List.of(java.net.Proxy.NO_PROXY);
						}
						return java.util.List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP, proxyAddress));
					}

					@Override
					public void connectFailed(java.net.URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
						// Ignore proxy connect failures.
					}
				});
			}
			catch (Exception ignored) {
				// Ignore invalid proxy URLs.
			}
		}
		return builder.build();
	}

	static boolean shouldBypass(String host, String bypassList) {
		if (host == null || host.isBlank() || bypassList == null || bypassList.isBlank()) {
			return false;
		}
		for (String entry : bypassList.split("[,\\s]+")) {
			String pattern = entry.trim();
			if (pattern.isEmpty()) {
				continue;
			}
			if (pattern.startsWith("*.")) {
				String suffix = pattern.substring(1);
				if (host.endsWith(suffix) || host.equals(pattern.substring(2))) {
					return true;
				}
			}
			else if (pattern.equalsIgnoreCase(host) || host.endsWith("." + pattern)) {
				return true;
			}
		}
		return false;
	}

	private ExecuteResult websocket(ExecuteCommand command, Map<String, String> variables, String url) throws Exception {
		if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
			return ExecuteResult.failure("WebSocket URL must start with ws:// or wss://");
		}
		StringBuilder received = new StringBuilder();
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
		java.net.http.WebSocket socket = httpClient(command).newWebSocketBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.buildAsync(URI.create(url), new java.net.http.WebSocket.Listener() {
				@Override
				public void onOpen(java.net.http.WebSocket webSocket) {
					webSocket.request(1);
				}

				@Override
				public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
					received.append(data);
					if (last) {
						received.append('\n');
					}
					webSocket.request(1);
					return null;
				}

				@Override
				public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket webSocket, int statusCode, String reason) {
					done.countDown();
					return null;
				}

				@Override
				public void onError(java.net.http.WebSocket webSocket, Throwable error) {
					if (received.length() > 0) {
						received.append('\n');
					}
					received.append(error.getMessage() == null ? "WebSocket error" : error.getMessage());
					done.countDown();
				}
			})
			.get(15, java.util.concurrent.TimeUnit.SECONDS);
		long started = System.nanoTime();
		String message = interpolate(command.getBody(), variables);
		if (message != null && !message.isBlank()) {
			socket.sendText(message, true).get(10, java.util.concurrent.TimeUnit.SECONDS);
		}
		int wait = Math.min(Math.max(command.getTimeoutSeconds(), 1), 8);
		done.await(wait, java.util.concurrent.TimeUnit.SECONDS);
		try {
			socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "done").get(2, java.util.concurrent.TimeUnit.SECONDS);
		}
		catch (Exception ignored) {
			socket.abort();
		}
		long elapsed = (System.nanoTime() - started) / 1_000_000;
		ExecuteResult result = new ExecuteResult();
		result.setOk(true);
		result.setStatus(101);
		result.setStatusText("Switching Protocols");
		result.setBody(received.toString());
		result.setTimeMs(elapsed);
		result.setSize(received.length());
		result.setContentType("text/plain");
		result.setChecks(Checks.evaluate(command.getAssertions(), 101, result.getBody(), List.of(), elapsed));
		return result;
	}

	private ExecuteResult send(ExecuteCommand command, Map<String, String> variables, List<StoredCookie> jar) throws Exception {
		long startedAt = System.currentTimeMillis();
		List<TimelineEntry> timeline = new ArrayList<>();
		timeline.add(new TimelineEntry("start", "Request started", startedAt, command.getMethod() + " " + command.getUrl()));
		long dnsAt = System.currentTimeMillis();
		timeline.add(new TimelineEntry("dns", "DNS lookup", dnsAt, "Resolved host"));
		ScriptRunner.run(ScriptRunner.preRequestScriptToRun(command.getPreRequestScript()), variables, command, store, command.getCollectionId(), scriptMode());
		timeline.add(new TimelineEntry("script", "Pre-request script", System.currentTimeMillis(), ""));
		if (RunnerContext.current() != null && RunnerContext.current().isSkipCurrent()) {
			ExecuteResult skipped = new ExecuteResult();
			skipped.setOk(true);
			skipped.setStatus(0);
			skipped.setStatusText("Skipped");
			skipped.setBody("");
			skipped.setTimeMs(System.currentTimeMillis() - startedAt);
			skipped.setTimeline(timeline);
			return skipped;
		}
		String originalMethod = command.getMethod() == null ? "GET" : command.getMethod().trim().toUpperCase(Locale.ROOT);
		String method = originalMethod;
		if ("SOAP".equals(method)) {
			command.setBodyType("soap");
			method = "POST";
		}
		if (!METHODS.contains(originalMethod)) {
			return ExecuteResult.failure("Unsupported method " + originalMethod);
		}

		String url = interpolate(command.getUrl(), variables).trim();
		if (url.isEmpty()) {
			return ExecuteResult.failure("Enter a URL");
		}
		if (url.contains("{{")) {
			return ExecuteResult.failure("Unresolved variable in the URL. Pick an environment, or replace {{name}}.");
		}
		ExecuteResult fault = FaultInjector.check(url);
		if (fault != null) {
			fault.setTimeline(timeline);
			return fault;
		}
		if ("WS".equals(method) || url.startsWith("ws://") || url.startsWith("wss://")) {
			ExecuteResult ws = websocket(command, variables, url);
			ws.setTimeline(timeline);
			return ws;
		}
		if ("SSE".equals(method)) {
			ExecuteResult sse = sse(command, variables, url, jar);
			sse.setTimeline(timeline);
			return sse;
		}
		if ("GRPC".equals(originalMethod) || url.startsWith("grpc://") || url.startsWith("grpcs://")) {
			PreparedBody prepared = prepareBody(command, variables);
			boolean stream = command.getExtras().isGrpcStream();
			ExecuteResult grpc = GrpcExecutor.execute(command, url, prepared.text(), stream);
			grpc.setTimeline(timeline);
			return grpc;
		}

		List<KeyValue> params = interpolateAll(command.getParams(), variables);
		if ("apikey".equals(command.getAuthType()) && "query".equalsIgnoreCase(command.getApiKeyIn())) {
			String name = interpolate(command.getApiKeyName(), variables).trim();
			if (!name.isEmpty()) {
				params.add(new KeyValue(name, interpolate(command.getApiKeyValue(), variables), true));
			}
		}

		URI uri = buildUri(url, params);
		String scheme = uri.getScheme();
		if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("grpc") && !scheme.equalsIgnoreCase("grpcs"))) {
			return ExecuteResult.failure("Only http, https, grpc, and grpcs URLs are allowed");
		}

		PreparedBody prepared = prepareBody(command, variables);
		int timeout = Math.min(Math.max(command.getTimeoutSeconds(), 1), 180);
		String httpMethod = "GRPC".equals(method) ? "POST" : method;
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout));
		applyHeaders(builder, interpolateAll(command.getHeaders(), variables), prepared);
		if ("GRPC".equals(method)) {
			builder.header("Content-Type", "application/grpc");
			builder.header("TE", "trailers");
			if (!command.getExtras().getGrpcService().isBlank()) {
				builder.header("grpc-service", command.getExtras().getGrpcService());
			}
		}
		if ("soap".equals(command.getBodyType()) && !command.getExtras().getSoapAction().isBlank()) {
			builder.header("SOAPAction", interpolate(command.getExtras().getSoapAction(), variables));
		}
		applyAuth(builder, command, variables, uri, prepared.text(), httpMethod);
		applyCookies(builder, jar, uri.getHost(), interpolateAll(command.getHeaders(), variables));
		builder.method(httpMethod, prepared.publisher(httpMethod));

		boolean http3 = isHttp3(command);
		long connectAt = System.currentTimeMillis();
		timeline.add(new TimelineEntry("connect", http3 ? "HTTP/3 connect" : "TCP/TLS connect", connectAt, uri.getHost()));

		long started = System.nanoTime();
		HttpResponse<java.io.InputStream> response;
		try {
			if (http3) {
				response = CurlHttp3.execute(builder.build(), prepared.text(), timeout);
			}
			else {
				response = sendWithAuth(httpClient(command), builder, command, variables, uri, prepared.text(), httpMethod);
			}
		}
		catch (Exception ex) {
			if (http3) {
				return ExecuteResult.failure(ex.getMessage() == null ? "HTTP/3 request failed" : ex.getMessage());
			}
			throw ex;
		}
		long elapsed = (System.nanoTime() - started) / 1_000_000;
		timeline.add(new TimelineEntry("ttfb", "Time to first byte", System.currentTimeMillis(), elapsed + " ms"));
		try (java.io.InputStream stream = response.body()) {
			byte[] bytes = stream.readNBytes(MAX_BODY_BYTES + 1);
			boolean truncated = bytes.length > MAX_BODY_BYTES;
			int length = truncated ? MAX_BODY_BYTES : bytes.length;
			HttpStatus status = HttpStatus.resolve(response.statusCode());
			List<KeyValue> headers = new ArrayList<>();
			response.headers().map().forEach((key, values) -> {
				if (key == null || key.startsWith(":")) {
					return;
				}
				for (String value : values) {
					headers.add(new KeyValue(key, value, true));
				}
			});
			String contentType = headerValue(headers, "content-type");
			boolean binary = isBinary(contentType);
			ExecuteResult result = new ExecuteResult();
			result.setOk(true);
			result.setStatus(response.statusCode());
			result.setStatusText(status == null ? "" : status.getReasonPhrase());
			result.setHeaders(headers);
			result.setTimeMs(elapsed);
			result.setSize(length);
			result.setContentType(contentType);
			result.setBinary(binary);
			if (binary) {
				result.setBodyBase64(Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(bytes, length)));
				result.setBody("");
			}
			else {
				String text = new String(bytes, 0, length, StandardCharsets.UTF_8);
				if (truncated) {
					text = text + "\n\n[Response truncated]";
				}
				result.setBody(text);
			}
			List<StoredCookie> seen = new ArrayList<>();
			for (KeyValue header : headers) {
				if ("set-cookie".equalsIgnoreCase(header.getKey())) {
					StoredCookie cookie = CookieJar.parse(header.getValue(), uri.getHost());
					if (cookie != null) {
						seen.add(cookie);
						if (jar != null) {
							CookieJar.remember(jar, cookie);
						}
					}
				}
			}
			result.setCookies(CookieJar.asPairs(seen));
			result.setChecks(Checks.evaluate(command.getAssertions(), result.getStatus(), result.getBody(), result.getHeaders(), result.getTimeMs()));
			try {
				String postResponseScript = ScriptRunner.mergedPostResponseScript(command.getPostResponseScript(), command.getPreRequestScript());
				ScriptRunner.ResponseApi responseApi = ScriptRunner.runResponse(postResponseScript, variables, result, store, command.getCollectionId(), scriptMode());
				result.getChecks().addAll(responseApi.getScriptChecks());
			}
			catch (IllegalArgumentException ex) {
				result.getChecks().add(new com.apiflow.model.CheckResult("script", false, ex.getMessage()));
			}
			timeline.add(new TimelineEntry("response", "Response received", System.currentTimeMillis(), result.getStatus() + " in " + result.getTimeMs() + "ms"));
			result.setTimeline(timeline);
			return result;
		}
	}

	private static boolean isHttp3(ExecuteCommand command) {
		String httpVersion = command.getExtras() == null ? "" : command.getExtras().getHttpVersion();
		return "h3".equalsIgnoreCase(httpVersion) || "http/3".equalsIgnoreCase(httpVersion) || "http3".equalsIgnoreCase(httpVersion);
	}

	private HttpResponse<java.io.InputStream> sendWithAuth(HttpClient client, HttpRequest.Builder builder, ExecuteCommand command, Map<String, String> variables, URI uri, String body, String method) throws Exception {
		boolean ntlm = "ntlm".equalsIgnoreCase(command.getAuthType());
		if (ntlm) {
			NtlmAuth.enable(interpolate(command.getAuthUsername(), variables), interpolate(command.getAuthPassword(), variables));
		}
		try {
			HttpResponse<java.io.InputStream> response = sendWithDigest(client, builder, command, variables, uri, body, method);
			if (ntlm || !"digest".equalsIgnoreCase(command.getAuthType()) || response.statusCode() != 401) {
				return response;
			}
			return retryDigest(client, builder, command, variables, uri, body, method, response);
		}
		finally {
			if (ntlm) {
				NtlmAuth.disable();
			}
		}
	}

	private HttpResponse<java.io.InputStream> sendWithDigest(HttpClient client, HttpRequest.Builder builder, ExecuteCommand command, Map<String, String> variables, URI uri, String body, String method) throws Exception {
		HttpRequest request = builder.build();
		return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
	}

	private HttpResponse<java.io.InputStream> retryDigest(HttpClient client, HttpRequest.Builder builder, ExecuteCommand command, Map<String, String> variables, URI uri, String body, String method, HttpResponse<java.io.InputStream> response) throws Exception {
		List<KeyValue> responseHeaders = new ArrayList<>();
		response.headers().map().forEach((key, values) -> values.forEach(value -> responseHeaders.add(new KeyValue(key, value, true))));
		String challenge = headerValue(responseHeaders, "www-authenticate");
		if (challenge == null || !challenge.toLowerCase(Locale.ROOT).contains("digest")) {
			return response;
		}
		response.body().close();
		String username = interpolate(command.getAuthUsername(), variables);
		String password = interpolate(command.getAuthPassword(), variables);
		String auth = DigestAuth.authorization(username, password, method, uri.getRawPath(), DigestAuth.parseChallenge(challenge));
		HttpRequest original = builder.build();
		HttpRequest.Builder retry = HttpRequest.newBuilder(uri).timeout(original.timeout().orElse(Duration.ofSeconds(30)));
		original.headers().map().forEach((key, values) -> {
			if (!"authorization".equalsIgnoreCase(key)) {
				for (String value : values) {
					retry.header(key, value);
				}
			}
		});
		retry.header("Authorization", auth);
		retry.method(method, publisher(method, body));
		return client.send(retry.build(), HttpResponse.BodyHandlers.ofInputStream());
	}

	private ExecuteResult sse(ExecuteCommand command, Map<String, String> variables, String url, List<StoredCookie> jar) throws Exception {
		List<KeyValue> params = interpolateAll(command.getParams(), variables);
		URI uri = buildUri(url, params);
		int timeout = Math.min(Math.max(command.getTimeoutSeconds(), 1), 180);
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout)).header("Accept", "text/event-stream");
		applyHeaders(builder, interpolateAll(command.getHeaders(), variables), PreparedBody.text("none", ""));
		applyAuth(builder, command, variables, uri, "", "GET");
		applyCookies(builder, jar, uri.getHost(), interpolateAll(command.getHeaders(), variables));
		builder.GET();
		long started = System.nanoTime();
		HttpResponse<java.io.InputStream> response = httpClient(command).send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
		StringBuilder events = new StringBuilder();
		try (java.io.InputStream stream = response.body()) {
			byte[] buffer = new byte[4096];
			long deadline = System.nanoTime() + Duration.ofSeconds(timeout).toNanos();
			while (System.nanoTime() < deadline) {
				if (stream.available() > 0) {
					int read = stream.read(buffer);
					if (read <= 0) {
						break;
					}
					events.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
				}
				else {
					Thread.sleep(50);
				}
			}
		}
		long elapsed = (System.nanoTime() - started) / 1_000_000;
		ExecuteResult result = new ExecuteResult();
		result.setOk(true);
		result.setStatus(response.statusCode());
		result.setBody(events.toString());
		result.setTimeMs(elapsed);
		result.setSize(events.length());
		result.setContentType("text/event-stream");
		return result;
	}

	private static PreparedBody prepareBody(ExecuteCommand command, Map<String, String> variables) {
		String bodyType = command.getBodyType() == null ? "none" : command.getBodyType();
		if ("json".equals(bodyType) || "text".equals(bodyType) || "xml".equals(bodyType) || "soap".equals(bodyType)) {
			String type = "soap".equals(bodyType) ? "text/xml; charset=utf-8" : ("json".equals(bodyType) ? "application/json" : ("xml".equals(bodyType) ? "application/xml" : "text/plain; charset=utf-8"));
			return PreparedBody.text(bodyType, interpolate(command.getBody(), variables), type);
		}
		if ("form".equals(bodyType)) {
			return PreparedBody.text(bodyType, formBody(interpolateAll(command.getForm(), variables)));
		}
		if ("graphql".equals(bodyType)) {
			String query = interpolate(command.getGraphqlQuery(), variables);
			String vars = interpolate(command.getGraphqlVariables(), variables).trim();
			if (vars.isEmpty()) {
				vars = "{}";
			}
			String json = "{\"query\":" + quote(query) + ",\"variables\":" + vars + "}";
			return PreparedBody.text("graphql", json);
		}
		if ("multipart".equals(bodyType)) {
			String boundary = "----ApiFlow" + System.nanoTime();
			byte[] bytes = multipart(boundary, interpolateAll(command.getForm(), variables), command.getFiles(), variables);
			return PreparedBody.bytes("multipart/form-data; boundary=" + boundary, bytes);
		}
		return PreparedBody.text("none", "");
	}

	private static void applyCookies(HttpRequest.Builder builder, List<StoredCookie> jar, String host, List<KeyValue> headers) {
		for (KeyValue header : headers) {
			if (header.getKey() != null && header.getKey().equalsIgnoreCase("cookie")) {
				return;
			}
		}
		String value = CookieJar.header(CookieJar.matching(jar, host));
		if (!value.isEmpty()) {
			builder.header("Cookie", value);
		}
	}

	private static String headerValue(List<KeyValue> headers, String name) {
		for (KeyValue header : headers) {
			if (header.getKey() != null && header.getKey().equalsIgnoreCase(name)) {
				return header.getValue() == null ? "" : header.getValue();
			}
		}
		return "";
	}

	private static boolean isBinary(String contentType) {
		String type = contentType.toLowerCase(Locale.ROOT);
		return type.startsWith("image/") || type.startsWith("audio/") || type.startsWith("video/")
				|| type.contains("pdf") || type.startsWith("application/octet-stream") || type.startsWith("application/zip");
	}

	private static HttpRequest.BodyPublisher publisher(String method, String body) {
		if (body == null || body.isEmpty() || "GET".equals(method) || "HEAD".equals(method)) {
			return HttpRequest.BodyPublishers.noBody();
		}
		return HttpRequest.BodyPublishers.ofString(body);
	}

	private static void applyHeaders(HttpRequest.Builder builder, List<KeyValue> headers, PreparedBody prepared) {
		boolean hasContentType = false;
		for (KeyValue header : enabled(headers)) {
			if (header.getKey() == null || header.getKey().isBlank()) {
				continue;
			}
			builder.header(header.getKey().trim(), header.getValue() == null ? "" : header.getValue());
			if (header.getKey().equalsIgnoreCase("content-type")) {
				hasContentType = true;
			}
		}
		if (!hasContentType && prepared.contentType != null) {
			builder.header("Content-Type", prepared.contentType);
		}
	}

	private static void applyAuth(HttpRequest.Builder builder, ExecuteCommand command, Map<String, String> variables, URI uri, String body, String method) {
		String authType = command.getAuthType() == null ? "none" : command.getAuthType();
		if ("bearer".equals(authType)) {
			String token = interpolate(command.getAuthToken(), variables).trim();
			if (!token.isEmpty()) {
				builder.header("Authorization", "Bearer " + token);
			}
		}
		else if ("basic".equals(authType)) {
			String username = interpolate(command.getAuthUsername(), variables);
			String password = interpolate(command.getAuthPassword(), variables);
			String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
			builder.header("Authorization", "Basic " + encoded);
		}
		else if ("apikey".equals(authType) && !"query".equalsIgnoreCase(command.getApiKeyIn())) {
			String name = interpolate(command.getApiKeyName(), variables).trim();
			if (!name.isEmpty()) {
				builder.header(name, interpolate(command.getApiKeyValue(), variables));
			}
		}
		else if ("aws".equals(authType)) {
			var extras = command.getExtras();
			var signed = AwsSigner.sign(
				interpolate(extras.getAwsAccessKey(), variables),
				interpolate(extras.getAwsSecretKey(), variables),
				interpolate(extras.getAwsRegion(), variables),
				interpolate(extras.getAwsService(), variables),
				method,
				uri,
				body,
				Map.of());
			signed.forEach(builder::header);
		}
		else if ("oauth1".equals(authType)) {
			var extras = command.getExtras();
			String header = OAuth1Signer.authorization(
				interpolate(extras.getOauth1ConsumerKey(), variables),
				interpolate(extras.getOauth1ConsumerSecret(), variables),
				interpolate(extras.getOauth1Token(), variables),
				interpolate(extras.getOauth1TokenSecret(), variables),
				method,
				uri,
				Map.of());
			builder.header("Authorization", header);
		}
		else if ("edgegrid".equals(authType)) {
			var extras = command.getExtras();
			Map<String, String> headers = new java.util.LinkedHashMap<>();
			for (KeyValue header : command.getHeaders()) {
				if (header != null && header.isEnabled() && header.getKey() != null) {
					headers.put(header.getKey(), interpolate(header.getValue(), variables));
				}
			}
			EdgeGridSigner.sign(
				interpolate(extras.getEdgeGridClientToken(), variables),
				interpolate(extras.getEdgeGridClientSecret(), variables),
				interpolate(extras.getEdgeGridAccessToken(), variables),
				method,
				uri,
				body,
				headers).forEach(builder::header);
		}
	}

	private static URI buildUri(String rawUrl, List<KeyValue> params) throws Exception {
		URI parsed = new URI(rawUrl);
		StringBuilder query = new StringBuilder(parsed.getRawQuery() == null ? "" : parsed.getRawQuery());
		for (KeyValue param : enabled(params)) {
			if (param.getKey() == null || param.getKey().isBlank()) {
				continue;
			}
			if (!query.isEmpty()) {
				query.append('&');
			}
			query.append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8));
			query.append('=');
			query.append(URLEncoder.encode(param.getValue() == null ? "" : param.getValue(), StandardCharsets.UTF_8));
		}
		String queryValue = query.isEmpty() ? null : query.toString();
		return new URI(parsed.getScheme(), parsed.getAuthority(), parsed.getPath(), queryValue, parsed.getFragment());
	}

	private static String formBody(List<KeyValue> fields) {
		StringBuilder body = new StringBuilder();
		for (KeyValue field : enabled(fields)) {
			if (field.getKey() == null || field.getKey().isBlank()) {
				continue;
			}
			if (!body.isEmpty()) {
				body.append('&');
			}
			body.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8));
			body.append('=');
			body.append(URLEncoder.encode(field.getValue() == null ? "" : field.getValue(), StandardCharsets.UTF_8));
		}
		return body.toString();
	}

	private static byte[] multipart(String boundary, List<KeyValue> fields, List<FilePart> files, Map<String, String> variables) {
		try {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			for (KeyValue field : enabled(fields)) {
				if (field.getKey() == null || field.getKey().isBlank()) {
					continue;
				}
				out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
				out.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
				out.write((field.getValue() == null ? "" : field.getValue()).getBytes(StandardCharsets.UTF_8));
				out.write("\r\n".getBytes(StandardCharsets.UTF_8));
			}
			if (files != null) {
				for (FilePart file : files) {
					if (file == null || !file.isEnabled() || file.getKey() == null || file.getKey().isBlank()) {
						continue;
					}
					String encoded = file.getDataBase64() == null ? "" : file.getDataBase64();
					int comma = encoded.indexOf(',');
					if (encoded.startsWith("data:") && comma > 0) {
						encoded = encoded.substring(comma + 1);
					}
					byte[] data = encoded.isBlank() ? new byte[0] : Base64.getDecoder().decode(encoded);
					String filename = interpolate(file.getFileName(), variables);
					String type = file.getContentType() == null || file.getContentType().isBlank() ? "application/octet-stream" : file.getContentType();
					out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
					out.write(("Content-Disposition: form-data; name=\"" + interpolate(file.getKey(), variables) + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
					out.write(("Content-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
					out.write(data);
					out.write("\r\n".getBytes(StandardCharsets.UTF_8));
				}
			}
			out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			return out.toByteArray();
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not build multipart body");
		}
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
	}

	private static List<KeyValue> interpolateAll(List<KeyValue> rows, Map<String, String> variables) {
		List<KeyValue> copy = new ArrayList<>();
		if (rows == null) {
			return copy;
		}
		for (KeyValue row : rows) {
			copy.add(new KeyValue(interpolate(row.getKey(), variables), interpolate(row.getValue(), variables), row.isEnabled()));
		}
		return copy;
	}

	private static List<KeyValue> enabled(List<KeyValue> rows) {
		List<KeyValue> enabled = new ArrayList<>();
		if (rows == null) {
			return enabled;
		}
		for (KeyValue row : rows) {
			if (row != null && row.isEnabled()) {
				enabled.add(row);
			}
		}
		return enabled;
	}

	private static final class PreparedBody {

		private final String contentType;
		private final String text;
		private final byte[] bytes;

		private PreparedBody(String contentType, String text, byte[] bytes) {
			this.contentType = contentType;
			this.text = text;
			this.bytes = bytes;
		}

		static PreparedBody text(String bodyType, String text) {
			String contentType = null;
			if ("json".equals(bodyType) || "graphql".equals(bodyType)) {
				contentType = text == null || text.isEmpty() ? null : "application/json";
			}
			else if ("text".equals(bodyType) && text != null && !text.isEmpty()) {
				contentType = "text/plain; charset=utf-8";
			}
			else if ("form".equals(bodyType) && text != null && !text.isEmpty()) {
				contentType = "application/x-www-form-urlencoded";
			}
			return new PreparedBody(contentType, text == null ? "" : text, null);
		}

		static PreparedBody text(String bodyType, String text, String contentType) {
			return new PreparedBody(text == null || text.isEmpty() ? null : contentType, text == null ? "" : text, null);
		}

		String text() {
			return text;
		}

		static PreparedBody bytes(String contentType, byte[] bytes) {
			return new PreparedBody(bytes.length == 0 ? null : contentType, "", bytes);
		}

		HttpRequest.BodyPublisher publisher(String method) {
			if (bytes != null) {
				if (("GET".equals(method) || "HEAD".equals(method)) && bytes.length == 0) {
					return HttpRequest.BodyPublishers.noBody();
				}
				return HttpRequest.BodyPublishers.ofByteArray(bytes);
			}
			return RequestExecutor.publisher(method, text);
		}

	}

}
