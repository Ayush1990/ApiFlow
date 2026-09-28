package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.apiflow.model.ExecuteCommand;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

@Component
public class GrpcStreamHub {

	private final java.util.Map<String, Session> sessions = new ConcurrentHashMap<>();

	public Opened open(ExecuteCommand command, String url, String mode) {
		if (command == null || command.getExtras().getGrpcService().isBlank()) {
			throw new IllegalArgumentException("Set gRPC service as package.Service/Method");
		}
		String streamMode = mode == null || mode.isBlank() ? "client" : mode.trim().toLowerCase();
		try {
			Channel channel = channel(url, command);
			if ("server".equals(streamMode)) {
				return openServerStream(channel, command, streamMode);
			}
			MethodDescriptor.MethodType type = "bidi".equals(streamMode)
				? MethodDescriptor.MethodType.BIDI_STREAMING
				: MethodDescriptor.MethodType.CLIENT_STREAMING;
			MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
				.setType(type)
				.setFullMethodName(command.getExtras().getGrpcService())
				.setRequestMarshaller(new GrpcExecutor.RawMarshaller())
				.setResponseMarshaller(new GrpcExecutor.RawMarshaller())
				.build();
			Session session = new Session(channel, descriptor, command, streamMode);
			session.start();
			String id = UUID.randomUUID().toString();
			sessions.put(id, session);
			return new Opened(id, streamMode);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not open gRPC stream: " + ex.getMessage());
		}
	}

	public Snapshot frames(String id) {
		Session session = require(id);
		return new Snapshot(session.closed, List.copyOf(session.frames));
	}

	public Snapshot send(String id, String jsonBody) {
		Session session = require(id);
		if (session.closed) {
			throw new IllegalArgumentException("gRPC stream is closed");
		}
		try {
			byte[] payload = encode(session.command, jsonBody);
			session.requestObserver.onNext(payload);
			session.frames.add(new Frame("out", decode(session.command, payload), "message"));
			trim(session);
			return new Snapshot(false, List.copyOf(session.frames));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException(ex.getMessage() == null ? "Could not send" : ex.getMessage());
		}
	}

	public Snapshot finish(String id) {
		Session session = require(id);
		if (session.closed) {
			throw new IllegalArgumentException("gRPC stream is closed");
		}
		try {
			session.requestObserver.onCompleted();
			if ("client".equals(session.mode)) {
				session.clientDone.await(30, TimeUnit.SECONDS);
				byte[] response = session.clientResponse.get();
				if (response != null) {
					session.frames.add(new Frame("in", decode(session.command, response), "message"));
				}
			}
			session.frames.add(new Frame("status", "Stream finished", "status"));
			trim(session);
			return new Snapshot(session.closed, List.copyOf(session.frames));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException(ex.getMessage() == null ? "Could not finish stream" : ex.getMessage());
		}
	}

	public void close(String id) {
		Session session = sessions.remove(id);
		if (session == null) {
			return;
		}
		session.closed = true;
		try {
			session.requestObserver.onCompleted();
		}
		catch (Exception ignored) {
		}
		if (session.channel != null) {
			GrpcChannelFactory.shutdown(session.channel);
		}
	}

	private Session require(String id) {
		Session session = sessions.get(id);
		if (session == null) {
			throw new IllegalArgumentException("That gRPC stream is closed");
		}
		return session;
	}

	private Opened openServerStream(Channel channel, ExecuteCommand command, String streamMode) throws Exception {
		MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
			.setType(MethodDescriptor.MethodType.SERVER_STREAMING)
			.setFullMethodName(command.getExtras().getGrpcService())
			.setRequestMarshaller(new GrpcExecutor.RawMarshaller())
			.setResponseMarshaller(new GrpcExecutor.RawMarshaller())
			.build();
		Session session = new Session(channel, descriptor, command, streamMode);
		byte[] payload = encode(command, command.getBody() == null || command.getBody().isBlank() ? "{}" : command.getBody());
		ClientCall<byte[], byte[]> clientCall = channel.newCall(descriptor, CallOptions.DEFAULT);
		ClientCalls.asyncServerStreamingCall(clientCall, payload, new StreamObserver<byte[]>() {
			@Override
			public void onNext(byte[] value) {
				session.frames.add(new Frame("in", decode(command, value), "message"));
				trim(session);
			}

			@Override
			public void onError(Throwable t) {
				session.closed = true;
				session.frames.add(new Frame("status", t.getMessage() == null ? "Stream error" : t.getMessage(), "status"));
			}

			@Override
			public void onCompleted() {
				session.closed = true;
				session.frames.add(new Frame("status", "Stream completed", "status"));
			}
		});
		session.frames.add(new Frame("out", decode(command, payload), "message"));
		session.frames.add(new Frame("status", "Connected (server)", "status"));
		String id = UUID.randomUUID().toString();
		sessions.put(id, session);
		return new Opened(id, streamMode);
	}

	private static Channel channel(String url, ExecuteCommand command) {
		return GrpcChannelFactory.open(url, command);
	}

	private static byte[] encode(ExecuteCommand command, String jsonBody) throws Exception {
		if (command.getExtras().getGrpcProto() != null && !command.getExtras().getGrpcProto().isBlank()) {
			return ProtoCodec.encode(command.getExtras().getGrpcProto(), command.getExtras().getGrpcService(), jsonBody == null ? "{}" : jsonBody);
		}
		return (jsonBody == null ? "{}" : jsonBody).getBytes(StandardCharsets.UTF_8);
	}

	private static String decode(ExecuteCommand command, byte[] payload) {
		try {
			if (command.getExtras().getGrpcProto() != null && !command.getExtras().getGrpcProto().isBlank()) {
				return ProtoCodec.decode(command.getExtras().getGrpcProto(), command.getExtras().getGrpcService(), payload, true);
			}
			return new String(payload, StandardCharsets.UTF_8);
		}
		catch (Exception ex) {
			return Base64.getEncoder().encodeToString(payload);
		}
	}

	private static void trim(Session session) {
		while (session.frames.size() > 200) {
			session.frames.remove(0);
		}
	}

	private static final class Session {

		private final Channel channel;
		private final MethodDescriptor<byte[], byte[]> descriptor;
		private final ExecuteCommand command;
		private final String mode;
		private StreamObserver<byte[]> requestObserver;
		private final AtomicReference<byte[]> clientResponse = new AtomicReference<>();
		private final CountDownLatch clientDone = new CountDownLatch(1);
		private volatile boolean closed;
		private final CopyOnWriteArrayList<Frame> frames = new CopyOnWriteArrayList<>();

		Session(Channel channel, MethodDescriptor<byte[], byte[]> descriptor, ExecuteCommand command, String mode) {
			this.channel = channel;
			this.descriptor = descriptor;
			this.command = command;
			this.mode = mode;
		}

		void start() {
			ClientCall<byte[], byte[]> clientCall = channel.newCall(descriptor, CallOptions.DEFAULT);
			if ("bidi".equals(mode)) {
				requestObserver = ClientCalls.asyncBidiStreamingCall(clientCall, new StreamObserver<byte[]>() {
					@Override
					public void onNext(byte[] value) {
						frames.add(new Frame("in", decode(command, value), "message"));
						trim(Session.this);
					}

					@Override
					public void onError(Throwable t) {
						closed = true;
						frames.add(new Frame("status", t.getMessage() == null ? "Stream error" : t.getMessage(), "status"));
					}

					@Override
					public void onCompleted() {
						closed = true;
						frames.add(new Frame("status", "Stream completed", "status"));
					}
				});
			}
			else {
				requestObserver = ClientCalls.asyncClientStreamingCall(clientCall, new StreamObserver<byte[]>() {
					@Override
					public void onNext(byte[] value) {
						clientResponse.set(value);
					}

					@Override
					public void onError(Throwable t) {
						closed = true;
						frames.add(new Frame("status", t.getMessage() == null ? "Stream error" : t.getMessage(), "status"));
						clientDone.countDown();
					}

					@Override
					public void onCompleted() {
						clientDone.countDown();
					}
				});
			}
			frames.add(new Frame("status", "Connected (" + mode + ")", "status"));
		}

	}

	public record Opened(String id, String mode) {
	}

	public record Frame(String direction, String text, String kind) {
	}

	public record Snapshot(boolean closed, List<Frame> frames) {
	}

}
