package com.apiflow.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

@Component
public class SocketIoHub {

	private final ConcurrentHashMap<String, Live> sessions = new ConcurrentHashMap<>();

	public Opened connect(String url, String event) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Socket.IO URL is required");
		}
		try {
			String id = UUID.randomUUID().toString();
			IO.Options options = IO.Options.builder().setForceNew(true).setReconnection(true).build();
			Socket socket = IO.socket(url, options);
			Live live = new Live(socket);
			socket.on(Socket.EVENT_CONNECT, args -> {
				live.frames.add(new Frame("status", "Connected", "connect"));
				trim(live);
			});
			socket.on(Socket.EVENT_DISCONNECT, args -> {
				live.closed = true;
				live.frames.add(new Frame("status", "Disconnected", "disconnect"));
			});
			socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
				live.frames.add(new Frame("status", String.valueOf(args.length > 0 ? args[0] : "connect error"), "error"));
			});
			String listenEvent = event == null || event.isBlank() ? "message" : event;
			socket.on(listenEvent, args -> {
				live.frames.add(new Frame("in", stringify(args), listenEvent));
				trim(live);
			});
			socket.connect();
			sessions.put(id, live);
			return new Opened(id);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Socket.IO connect failed: " + ex.getMessage());
		}
	}

	public Snapshot emit(String id, String event, String payload) {
		Live live = require(id);
		if (live.closed) {
			throw new IllegalArgumentException("Socket.IO session closed");
		}
		String eventName = event == null || event.isBlank() ? "message" : event;
		live.socket.emit(eventName, payload == null ? "" : payload);
		live.frames.add(new Frame("out", payload, eventName));
		trim(live);
		return snapshot(live);
	}

	public Snapshot frames(String id) {
		return snapshot(require(id));
	}

	public void close(String id) {
		Live live = sessions.remove(id);
		if (live == null) {
			return;
		}
		live.closed = true;
		try {
			live.socket.disconnect();
			live.socket.close();
		}
		catch (Exception ignored) {
			// best effort
		}
	}

	private Live require(String id) {
		Live live = sessions.get(id);
		if (live == null) {
			throw new IllegalArgumentException("Socket.IO session closed");
		}
		return live;
	}

	private Snapshot snapshot(Live live) {
		return new Snapshot(live.closed, List.copyOf(live.frames));
	}

	private static String stringify(Object[] args) {
		if (args == null || args.length == 0) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (Object arg : args) {
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(arg);
		}
		return builder.toString();
	}

	private static void trim(Live live) {
		while (live.frames.size() > 200) {
			live.frames.remove(0);
		}
	}

	private static final class Live {
		private final Socket socket;
		private volatile boolean closed;
		private final CopyOnWriteArrayList<Frame> frames = new CopyOnWriteArrayList<>();

		private Live(Socket socket) {
			this.socket = socket;
		}
	}

	public record Opened(String id) {
	}

	public record Frame(String direction, String text, String event) {
	}

	public record Snapshot(boolean closed, List<Frame> frames) {
	}

}
