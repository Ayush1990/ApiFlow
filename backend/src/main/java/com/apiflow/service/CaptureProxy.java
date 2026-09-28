package com.apiflow.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javax.net.ssl.SSLSocket;

final class CaptureProxy {

	private final Consumer<Exchange> sink;
	private final Path directory;
	private volatile boolean running;
	private ServerSocket server;
	private LocalCa ca;

	CaptureProxy(Path directory, Consumer<Exchange> sink) {
		this.directory = directory;
		this.sink = sink;
	}

	synchronized int start(int port) throws Exception {
		stop();
		ca = LocalCa.loadOrCreate(directory);
		server = new ServerSocket(port <= 0 ? 8888 : port);
		running = true;
		Thread thread = new Thread(this::acceptLoop, "apiflow-capture");
		thread.setDaemon(true);
		thread.start();
		return server.getLocalPort();
	}

	synchronized void stop() {
		running = false;
		if (server != null) {
			try {
				server.close();
			}
			catch (Exception ignored) {
				// already closed
			}
		}
	}

	String certificatePath() {
		return directory.resolve("apiflow-ca.crt").toString();
	}

	private void acceptLoop() {
		while (running && server != null && !server.isClosed()) {
			try {
				Socket client = server.accept();
				Thread worker = new Thread(() -> handle(client), "apiflow-capture-conn");
				worker.setDaemon(true);
				worker.start();
			}
			catch (Exception ex) {
				if (!running) {
					break;
				}
			}
		}
	}

	private void handle(Socket client) {
		try (client) {
			client.setSoTimeout(15000);
			HttpMessage message = readMessage(client.getInputStream());
			if (message == null || message.start.isBlank()) {
				return;
			}
			String[] parts = message.start.split(" ");
			String method = parts.length > 0 ? parts[0] : "GET";
			String target = parts.length > 1 ? parts[1] : "/";
			if ("CONNECT".equalsIgnoreCase(method)) {
				String host = target;
				int port = 443;
				int colon = target.lastIndexOf(':');
				if (colon > 0) {
					host = target.substring(0, colon);
					port = Integer.parseInt(target.substring(colon + 1));
				}
				client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.UTF_8));
				client.getOutputStream().flush();
				interceptTls(client, host, port);
				return;
			}
			URI uri = URI.create(target);
			String host = header(message.headers, "host");
			if ((host == null || host.isBlank()) && uri.getHost() != null) {
				host = uri.getHost();
			}
			int port = uri.getPort() > 0 ? uri.getPort() : 80;
			String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? target : uri.getRawPath();
			if (uri.getRawQuery() != null) {
				path = path + "?" + uri.getRawQuery();
			}
			byte[] payload = rewrite(message, method, path);
			try (Socket upstream = new Socket(hostName(host), port)) {
				upstream.setSoTimeout(15000);
				upstream.getOutputStream().write(payload);
				upstream.getOutputStream().flush();
				byte[] response = upstream.getInputStream().readNBytes(65536);
				client.getOutputStream().write(response);
				client.getOutputStream().flush();
				sink.accept(new Exchange(method, target, pathOf(target), statusOf(response)));
			}
		}
		catch (Exception ignored) {
			// drop broken proxy connections
		}
	}

	private void interceptTls(Socket client, String host, int port) throws Exception {
		SSLSocket browser = (SSLSocket) ca.serverContext(host).getSocketFactory().createSocket(client, host, port, true);
		browser.setUseClientMode(false);
		browser.startHandshake();
		HttpMessage message = readMessage(browser.getInputStream());
		if (message == null) {
			browser.close();
			return;
		}
		String[] parts = message.start.split(" ");
		String method = parts.length > 0 ? parts[0] : "GET";
		String path = parts.length > 1 ? parts[1] : "/";
		byte[] payload = rewrite(message, method, path);
		try (SSLSocket upstream = (SSLSocket) SSLSocketFactoryHolder.client().createSocket(host, port)) {
			upstream.setSoTimeout(15000);
			upstream.startHandshake();
			upstream.getOutputStream().write(payload);
			upstream.getOutputStream().flush();
			byte[] response = upstream.getInputStream().readNBytes(65536);
			browser.getOutputStream().write(response);
			browser.getOutputStream().flush();
			sink.accept(new Exchange(method, "https://" + host + path, pathOf(path), statusOf(response)));
		}
		finally {
			browser.close();
		}
	}

	private static byte[] rewrite(HttpMessage message, String method, String path) {
		StringBuilder text = new StringBuilder();
		text.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
		message.headers.forEach((key, value) -> {
			if (!"proxy-connection".equalsIgnoreCase(key)) {
				text.append(key).append(": ").append(value).append("\r\n");
			}
		});
		text.append("\r\n");
		byte[] head = text.toString().getBytes(StandardCharsets.UTF_8);
		byte[] all = new byte[head.length + message.body.length];
		System.arraycopy(head, 0, all, 0, head.length);
		System.arraycopy(message.body, 0, all, head.length, message.body.length);
		return all;
	}

	private static HttpMessage readMessage(InputStream input) throws Exception {
		ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
		int previous = -1;
		while (true) {
			int next = input.read();
			if (next < 0) {
				break;
			}
			headerBytes.write(next);
			if (previous == '\n' && headerBytes.size() >= 4) {
				byte[] current = headerBytes.toByteArray();
				int length = current.length;
				if (length >= 4 && current[length - 4] == '\r' && current[length - 3] == '\n' && current[length - 2] == '\r' && current[length - 1] == '\n') {
					break;
				}
			}
			previous = next;
			if (headerBytes.size() > 65536) {
				break;
			}
		}
		String headerText = headerBytes.toString(StandardCharsets.UTF_8);
		int split = headerText.indexOf("\r\n\r\n");
		if (split < 0) {
			return null;
		}
		String[] lines = headerText.substring(0, split).split("\r\n");
		Map<String, String> headers = new LinkedHashMap<>();
		for (int index = 1; index < lines.length; index++) {
			int colon = lines[index].indexOf(':');
			if (colon > 0) {
				headers.put(lines[index].substring(0, colon).trim(), lines[index].substring(colon + 1).trim());
			}
		}
		int contentLength = 0;
		String length = header(headers, "content-length");
		if (length != null && !length.isBlank()) {
			contentLength = Integer.parseInt(length.trim());
		}
		byte[] body = contentLength <= 0 ? new byte[0] : input.readNBytes(contentLength);
		return new HttpMessage(lines[0], headers, body);
	}

	private static String header(Map<String, String> headers, String name) {
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				return entry.getValue();
			}
		}
		return "";
	}

	private static String hostName(String host) {
		if (host == null) {
			return "";
		}
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(']') < 0) {
			return host.substring(0, colon);
		}
		return host;
	}

	private static String pathOf(String value) {
		if (value == null || value.isBlank()) {
			return "/";
		}
		int scheme = value.indexOf("://");
		String path = value;
		if (scheme >= 0) {
			int slash = value.indexOf('/', scheme + 3);
			path = slash < 0 ? "/" : value.substring(slash);
		}
		int query = path.indexOf('?');
		return query < 0 ? path : path.substring(0, query);
	}

	private static int statusOf(byte[] response) {
		String line = new String(response, 0, Math.min(response.length, 40), StandardCharsets.UTF_8);
		int space = line.indexOf(' ');
		if (space < 0) {
			return 0;
		}
		try {
			return Integer.parseInt(line.substring(space + 1, Math.min(line.length(), space + 4)).trim());
		}
		catch (Exception ex) {
			return 0;
		}
	}

	private record HttpMessage(String start, Map<String, String> headers, byte[] body) {
	}

	record Exchange(String method, String url, String path, int status) {
	}

	private static final class SSLSocketFactoryHolder {
		private static javax.net.ssl.SSLSocketFactory client() throws Exception {
			return javax.net.ssl.SSLContext.getDefault().getSocketFactory();
		}
	}

}
