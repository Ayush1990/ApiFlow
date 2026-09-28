package com.apiflow.service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSession;

final class CurlHttp3 {

	private CurlHttp3() {
	}

	static HttpResponse<java.io.InputStream> execute(HttpRequest request, String body, int timeoutSeconds) throws Exception {
		Path headerFile = Files.createTempFile("apiflow-h3-headers", ".txt");
		Path bodyFile = Files.createTempFile("apiflow-h3-body", ".bin");
		Path requestBody = body == null || body.isBlank() ? null : Files.createTempFile("apiflow-h3-request", ".bin");
		try {
			if (requestBody != null) {
				Files.writeString(requestBody, body, StandardCharsets.UTF_8);
			}
			List<String> command = new ArrayList<>();
			command.add("curl");
			command.add("--http3-only");
			command.add("-sS");
			command.add("-D");
			command.add(headerFile.toString());
			command.add("-o");
			command.add(bodyFile.toString());
			command.add("-X");
			command.add(request.method());
			command.add("--max-time");
			command.add(String.valueOf(Math.max(timeoutSeconds, 1)));
			request.headers().map().forEach((key, values) -> {
				for (String value : values) {
					command.add("-H");
					command.add(key + ": " + value);
				}
			});
			if (requestBody != null && !"GET".equalsIgnoreCase(request.method()) && !"HEAD".equalsIgnoreCase(request.method())) {
				command.add("--data-binary");
				command.add("@" + requestBody);
			}
			command.add(request.uri().toString());
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			byte[] output = process.getInputStream().readAllBytes();
			boolean finished = process.waitFor(timeoutSeconds + 5L, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				throw new IllegalArgumentException("HTTP/3 request timed out");
			}
			if (process.exitValue() != 0) {
				String message = new String(output, StandardCharsets.UTF_8).trim();
				if (message.toLowerCase().contains("http3") || message.toLowerCase().contains("option")) {
					throw new IllegalArgumentException("HTTP/3 needs a curl build with HTTP/3. " + message);
				}
				throw new IllegalArgumentException(message.isBlank() ? "HTTP/3 request failed" : message);
			}
			int status = 0;
			MapHeaders headers = new MapHeaders();
			for (String line : Files.readString(headerFile, StandardCharsets.UTF_8).split("\\R")) {
				if (line.startsWith("HTTP/")) {
					String[] parts = line.split(" ");
					if (parts.length > 1) {
						status = Integer.parseInt(parts[1]);
					}
					headers.clear();
					continue;
				}
				int split = line.indexOf(':');
				if (split > 0) {
					headers.add(line.substring(0, split).trim(), line.substring(split + 1).trim());
				}
			}
			byte[] responseBody = Files.exists(bodyFile) ? Files.readAllBytes(bodyFile) : new byte[0];
			return new BytesResponse(request, status, headers.toHeaders(), responseBody);
		}
		finally {
			Files.deleteIfExists(headerFile);
			Files.deleteIfExists(bodyFile);
			if (requestBody != null) {
				Files.deleteIfExists(requestBody);
			}
		}
	}

	private static final class MapHeaders {
		private final LinkedHashMap<String, List<String>> values = new LinkedHashMap<>();

		void add(String key, String value) {
			values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
		}

		void clear() {
			values.clear();
		}

		HttpHeaders toHeaders() {
			return HttpHeaders.of(values, (key, value) -> true);
		}
	}

	private record BytesResponse(HttpRequest request, int statusCode, HttpHeaders headers, byte[] payload) implements HttpResponse<java.io.InputStream> {
		@Override
		public java.io.InputStream body() {
			return new ByteArrayInputStream(payload);
		}

		@Override
		public Optional<HttpResponse<java.io.InputStream>> previousResponse() {
			return Optional.empty();
		}

		@Override
		public Optional<SSLSession> sslSession() {
			return Optional.empty();
		}

		@Override
		public URI uri() {
			return request.uri();
		}

		@Override
		public HttpClient.Version version() {
			return HttpClient.Version.HTTP_2;
		}
	}

}
