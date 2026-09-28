package com.apiflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

public final class ScriptHttp {

	private ScriptHttp() {
	}

	public static class SyncClient {

		private final Map<String, String> variables;

		SyncClient(Map<String, String> variables) {
			this.variables = variables;
		}

		@HostAccess.Export
		public SyncResponse get(String url) {
			return request("GET", url, "", Map.of());
		}

		@HostAccess.Export
		public SyncResponse post(String url, String body) {
			return request("POST", url, body, Map.of("Content-Type", "application/json"));
		}

		@HostAccess.Export
		public SyncResponse request(String method, String url, String body, Map<String, String> headers) {
			try {
				String resolved = VariableResolver.interpolate(url, variables);
				HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(resolved))
					.timeout(Duration.ofSeconds(30))
					.method(method, body == null || body.isBlank() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(VariableResolver.interpolate(body, variables)));
				if (headers != null) {
					for (Map.Entry<String, String> entry : headers.entrySet()) {
						if (entry.getKey() != null && !entry.getKey().isBlank()) {
							builder.header(entry.getKey(), VariableResolver.interpolate(entry.getValue(), variables));
						}
					}
				}
				HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
				return new SyncResponse(response.statusCode(), response.body(), response.headers().map());
			}
			catch (Exception ex) {
				throw new IllegalArgumentException("Script request failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
			}
		}
	}

	public static class SyncResponse {

		private final int status;
		private final String body;
		private final Map<String, java.util.List<String>> headers;

		SyncResponse(int status, String body, Map<String, java.util.List<String>> headers) {
			this.status = status;
			this.body = body == null ? "" : body;
			this.headers = headers == null ? Map.of() : headers;
		}

		@HostAccess.Export
		public int getStatus() {
			return status;
		}

		@HostAccess.Export
		public String getBody() {
			return body;
		}

		@HostAccess.Export
		public String getHeader(String name) {
			if (name == null) {
				return "";
			}
			for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
				if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
					return entry.getValue().get(0);
				}
			}
			return "";
		}
	}

}
