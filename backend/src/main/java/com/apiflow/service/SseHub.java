package com.apiflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;

@Component
public class SseHub {

	private final Map<String, Live> live = new ConcurrentHashMap<>();
	private final ExecutorService executor = Executors.newCachedThreadPool();

	public OpenResult open(String url, Map<String, String> headers, int timeoutSeconds) {
		String id = UUID.randomUUID().toString();
		Live session = new Live(id, url);
		live.put(id, session);
		executor.submit(() -> stream(session, headers, timeoutSeconds));
		return new OpenResult(id);
	}

	public FrameList frames(String id) {
		Live session = live.get(id);
		return session == null ? new FrameList(List.of(), true) : new FrameList(List.copyOf(session.frames), session.closed);
	}

	public void close(String id) {
		Live session = live.remove(id);
		if (session != null) {
			session.closed = true;
		}
	}

	private void stream(Live session, Map<String, String> headers, int timeoutSeconds) {
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(session.url))
				.timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
				.header("Accept", "text/event-stream")
				.GET();
			if (headers != null) {
				for (Map.Entry<String, String> entry : headers.entrySet()) {
					if (entry.getKey() != null && entry.getValue() != null) {
						builder.header(entry.getKey(), entry.getValue());
					}
				}
			}
			HttpResponse<java.io.InputStream> response = HttpClient.newHttpClient()
				.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
			session.frames.add(new Frame("meta", "status " + response.statusCode()));
			try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
				String line;
				StringBuilder event = new StringBuilder();
				while (!session.closed && (line = reader.readLine()) != null) {
					if (line.isBlank()) {
						if (!event.isEmpty()) {
							session.frames.add(new Frame("event", event.toString().trim()));
							event.setLength(0);
						}
						continue;
					}
					event.append(line).append('\n');
				}
			}
		}
		catch (Exception ex) {
			session.frames.add(new Frame("error", ex.getMessage() == null ? "SSE failed" : ex.getMessage()));
		}
		finally {
			session.closed = true;
		}
	}

	public record OpenResult(String id) {
	}

	public record FrameList(List<Frame> frames, boolean closed) {
	}

	public record Frame(String kind, String text) {
	}

	private static final class Live {
		private final String id;
		private final String url;
		private final CopyOnWriteArrayList<Frame> frames = new CopyOnWriteArrayList<>();
		private volatile boolean closed;

		private Live(String id, String url) {
			this.id = id;
			this.url = url;
		}
	}

}
