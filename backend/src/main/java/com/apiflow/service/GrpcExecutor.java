package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

public final class GrpcExecutor {

	private GrpcExecutor() {
	}

	public static ExecuteResult execute(ExecuteCommand command, String url, String body, boolean stream) {
		long started = System.nanoTime();
		Channel channel = null;
		try {
			channel = GrpcChannelFactory.open(url, command);
			String method = command.getExtras().getGrpcService();
			if (method == null || method.isBlank()) {
				return ExecuteResult.failure("Set gRPC method as package.Service/Method in Settings");
			}
			byte[] payload = encodePayload(command, body);
			MethodDescriptor.MethodType type = stream ? MethodDescriptor.MethodType.SERVER_STREAMING : MethodDescriptor.MethodType.UNARY;
			MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
				.setType(type)
				.setFullMethodName(method)
				.setRequestMarshaller(new RawMarshaller())
				.setResponseMarshaller(new RawMarshaller())
				.build();
			String responseBody;
			if (stream) {
				Iterator<byte[]> responses = ClientCalls.blockingServerStreamingCall(channel, descriptor, CallOptions.DEFAULT, payload);
				StringBuilder out = new StringBuilder("[");
				boolean first = true;
				while (responses.hasNext()) {
					if (!first) {
						out.append(',');
					}
					first = false;
					out.append(decodePayload(command, responses.next()));
				}
				out.append(']');
				responseBody = out.toString();
			}
			else {
				byte[] response = ClientCalls.blockingUnaryCall(channel, descriptor, CallOptions.DEFAULT, payload);
				responseBody = response == null ? "" : decodePayload(command, response);
			}
			ExecuteResult result = new ExecuteResult();
			result.setOk(true);
			result.setStatus(200);
			result.setStatusText("OK");
			result.setBody(responseBody);
			result.setContentType("application/json");
			result.setTimeMs((System.nanoTime() - started) / 1_000_000);
			result.setSize(responseBody.length());
			return result;
		}
		catch (Exception ex) {
			return ExecuteResult.failure("gRPC call failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
		}
		finally {
			if (channel != null) {
				GrpcChannelFactory.shutdown(channel);
			}
		}
	}

	public static List<String> reflectMethods(String url, String protoText) throws Exception {
		if (protoText != null && !protoText.isBlank()) {
			return ProtoCodec.listMethods(protoText);
		}
		return reflect(url);
	}

	public static List<String> reflect(String url) throws Exception {
		return reflect(url, null);
	}

	public static List<String> reflect(String url, com.apiflow.model.RequestExtras extras) throws Exception {
		Channel channel = GrpcChannelFactory.open(url, extras, null);
		try {
			List<String> services = new ArrayList<>();
			ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
			java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
			StreamObserver<ServerReflectionRequest> requestObserver = stub.serverReflectionInfo(new StreamObserver<>() {
				@Override
				public void onNext(ServerReflectionResponse value) {
					if (value.hasListServicesResponse()) {
						value.getListServicesResponse().getServiceList().forEach(service -> services.add(service.getName()));
					}
					done.countDown();
				}

				@Override
				public void onError(Throwable t) {
					done.countDown();
				}

				@Override
				public void onCompleted() {
					done.countDown();
				}
			});
			requestObserver.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
			requestObserver.onCompleted();
			done.await(10, TimeUnit.SECONDS);
			return services;
		}
		finally {
			GrpcChannelFactory.shutdown(channel);
		}
	}

	public static String normalizeUrl(String url) {
		if (url.startsWith("grpc://")) {
			return "http://" + url.substring("grpc://".length());
		}
		if (url.startsWith("grpcs://")) {
			return "https://" + url.substring("grpcs://".length());
		}
		return url;
	}

	public static boolean isTls(String url) {
		return url.startsWith("https") || url.startsWith("grpcs://");
	}

	private static byte[] encodePayload(ExecuteCommand command, String body) throws Exception {
		String proto = command.getExtras().getGrpcProto();
		if (proto != null && !proto.isBlank()) {
			return ProtoCodec.encode(proto, command.getExtras().getGrpcService(), body == null ? "" : body);
		}
		return body == null || body.isBlank() ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
	}

	private static String decodePayload(ExecuteCommand command, byte[] bytes) {
		try {
			String proto = command.getExtras().getGrpcProto();
			if (proto != null && !proto.isBlank()) {
				return ProtoCodec.decode(proto, command.getExtras().getGrpcService(), bytes, true);
			}
		}
		catch (Exception ignored) {
			// Fall back to UTF-8 below.
		}
		return '"' + escape(new String(bytes, StandardCharsets.UTF_8)) + '"';
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	public static final class RawMarshaller implements MethodDescriptor.Marshaller<byte[]> {
		@Override
		public byte[] parse(java.io.InputStream stream) {
			try {
				return stream.readAllBytes();
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public java.io.InputStream stream(byte[] value) {
			return new java.io.ByteArrayInputStream(value == null ? new byte[0] : value);
		}
	}

}
