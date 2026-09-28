package com.apiflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.springframework.stereotype.Component;

import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestExtras;

@Component
public class WsHub {

	private final java.util.Map<String, Live> sessions = new ConcurrentHashMap<>();

	public Opened open(String url, List<KeyValue> headers, RequestExtras extras, String text) {
		if (url == null || (!url.startsWith("ws://") && !url.startsWith("wss://"))) {
			throw new IllegalArgumentException("WebSocket URL must start with ws:// or wss://");
		}
		try {
			Live live = new Live();
			HttpClient.Builder clientBuilder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
			SSLContext context = ClientCerts.context(extras);
			if (context != null) {
				clientBuilder.sslContext(context);
			}
			WebSocket.Builder builder = clientBuilder.build().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
			if (extras != null && extras.getWsSubprotocol() != null && !extras.getWsSubprotocol().isBlank()) {
				builder.subprotocols(extras.getWsSubprotocol());
			}
			if (headers != null) {
				for (KeyValue header : headers) {
					if (header != null && header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
						builder.header(header.getKey().trim(), header.getValue() == null ? "" : header.getValue());
					}
				}
			}
			WebSocket socket = builder.buildAsync(URI.create(url), listener(live)).get(15, TimeUnit.SECONDS);
			live.socket = socket;
			if (text != null && !text.isBlank()) {
				socket.sendText(text, true).get(10, TimeUnit.SECONDS);
				live.frames.add(new Frame("out", text, "text"));
			}
			String id = UUID.randomUUID().toString();
			sessions.put(id, live);
			return new Opened(id);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			String message = ex.getMessage() == null ? "Could not open the WebSocket" : ex.getMessage();
			throw new IllegalArgumentException(message);
		}
	}

	public Snapshot frames(String id) {
		Live live = sessions.get(id);
		if (live == null) {
			throw new IllegalArgumentException("That WebSocket is closed");
		}
		return new Snapshot(live.closed, List.copyOf(live.frames));
	}

	public Snapshot send(String id, String text) {
		Live live = require(id);
		if (live.closed || live.socket == null) {
			throw new IllegalArgumentException("WebSocket is closed");
		}
		try {
			String message = text == null ? "" : text;
			live.socket.sendText(message, true).get(10, TimeUnit.SECONDS);
			live.frames.add(new Frame("out", message, "text"));
			trim(live);
			return new Snapshot(false, List.copyOf(live.frames));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException(ex.getMessage() == null ? "Could not send" : ex.getMessage());
		}
	}

	public Snapshot sendBinary(String id, String base64) {
		Live live = require(id);
		if (live.closed || live.socket == null) {
			throw new IllegalArgumentException("WebSocket is closed");
		}
		try {
			byte[] bytes = Base64.getDecoder().decode(base64 == null ? "" : base64);
			live.socket.sendBinary(ByteBuffer.wrap(bytes), true).get(10, TimeUnit.SECONDS);
			live.frames.add(new Frame("out", base64, "binary"));
			trim(live);
			return new Snapshot(false, List.copyOf(live.frames));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException(ex.getMessage() == null ? "Could not send binary frame" : ex.getMessage());
		}
	}

	public Snapshot sendCloseFrame(String id, int code, String reason) {
		Live live = require(id);
		if (live.closed || live.socket == null) {
			throw new IllegalArgumentException("WebSocket is closed");
		}
		int closeCode = code <= 0 ? WebSocket.NORMAL_CLOSURE : code;
		String closeReason = reason == null ? "" : reason;
		try {
			live.socket.sendClose(closeCode, closeReason).get(10, TimeUnit.SECONDS);
			live.frames.add(new Frame("out", "close", closeCode + " " + closeReason));
			live.closed = true;
			trim(live);
			return new Snapshot(true, List.copyOf(live.frames));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException(ex.getMessage() == null ? "Could not send close frame" : ex.getMessage());
		}
	}

	public Snapshot ping(String id) {
		Live live = require(id);
		if (live.closed || live.socket == null) {
			throw new IllegalArgumentException("WebSocket is closed");
		}
		try {
			live.socket.sendPing(ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
			live.frames.add(new Frame("out", "ping", "ping"));
			trim(live);
			return new Snapshot(false, List.copyOf(live.frames));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException(ex.getMessage() == null ? "Could not ping" : ex.getMessage());
		}
	}

	public void close(String id) {
		Live live = sessions.get(id);
		if (live == null) {
			return;
		}
		live.closed = true;
		if (live.socket != null) {
			try {
				live.socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(2, TimeUnit.SECONDS);
			}
			catch (Exception ex) {
				live.socket.abort();
			}
		}
		sessions.remove(id);
	}

	private Live require(String id) {
		Live live = sessions.get(id);
		if (live == null) {
			throw new IllegalArgumentException("That WebSocket is closed");
		}
		return live;
	}

	private WebSocket.Listener listener(Live live) {
		return new WebSocket.Listener() {
			@Override
			public void onOpen(WebSocket webSocket) {
				live.frames.add(new Frame("status", "Connected", "status"));
				webSocket.request(1);
			}

			@Override
			public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
				live.buffer.append(data);
				if (last) {
					live.frames.add(new Frame("in", live.buffer.toString(), "text"));
					live.buffer.setLength(0);
					trim(live);
				}
				webSocket.request(1);
				return null;
			}

			@Override
			public java.util.concurrent.CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
				byte[] bytes = new byte[data.remaining()];
				data.get(bytes);
				live.frames.add(new Frame("in", Base64.getEncoder().encodeToString(bytes), "binary"));
				trim(live);
				webSocket.request(1);
				return null;
			}

			@Override
			public java.util.concurrent.CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
				live.frames.add(new Frame("in", "pong", "pong"));
				trim(live);
				webSocket.request(1);
				return null;
			}

			@Override
			public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
				live.closed = true;
				live.frames.add(new Frame("status", "Closed" + (reason == null || reason.isBlank() ? "" : ": " + reason), "status"));
				return null;
			}

			@Override
			public void onError(WebSocket webSocket, Throwable error) {
				live.closed = true;
				live.frames.add(new Frame("status", error.getMessage() == null ? "WebSocket error" : error.getMessage(), "status"));
			}
		};
	}

	private static void trim(Live live) {
		while (live.frames.size() > 200) {
			live.frames.remove(0);
		}
	}

	private static final class Live {
		private WebSocket socket;
		private volatile boolean closed;
		private final StringBuilder buffer = new StringBuilder();
		private final CopyOnWriteArrayList<Frame> frames = new CopyOnWriteArrayList<>();
	}

	public record Opened(String id) {
	}

	public record Frame(String direction, String text, String kind) {
	}

	public record Snapshot(boolean closed, List<Frame> frames) {
	}

}
