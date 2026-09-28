package com.apiflow.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.RequestCollection;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.ServerCalls;

final class GrpcMockServer {

	private static final AtomicReference<Server> SERVER = new AtomicReference<>();

	private GrpcMockServer() {
	}

	static int start(List<RequestCollection> collections, int port) {
		try {
			stop();
			ServerBuilder<?> builder = ServerBuilder.forPort(port);
			int methods = 0;
			for (RequestCollection collection : collections) {
				for (ApiRequest request : collection.getRequests()) {
					if (!"GRPC".equalsIgnoreCase(request.getMethod()) && (request.getUrl() == null || !request.getUrl().contains("grpc"))) {
						continue;
					}
					String service = request.getExtras() == null ? "" : request.getExtras().getGrpcService();
					String method = methodName(request);
					if (service.isBlank()) {
						service = "apiflow.mock.Examples";
					}
					String example = request.getExtras() != null && !request.getExtras().getMockBody().isBlank()
						? request.getExtras().getMockBody()
						: request.getBody();
					builder.addService(serviceDefinition(service, method, example == null ? "{}" : example));
					methods++;
				}
			}
			if (methods == 0) {
				builder.addService(serviceDefinition("apiflow.mock.Examples", "GetExample", "{\"ok\":true}"));
			}
			builder.addService(ProtoReflectionService.newInstance());
			Server server = builder.build().start();
			SERVER.set(server);
			return server.getPort();
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not start gRPC mock: " + ex.getMessage());
		}
	}

	static void stop() {
		Server current = SERVER.getAndSet(null);
		if (current != null) {
			current.shutdownNow();
		}
	}

	private static ServerServiceDefinition serviceDefinition(String service, String method, String example) throws Exception {
		String serviceName = service.contains(".") ? service : "apiflow.mock." + service;
		String simple = serviceName.substring(serviceName.lastIndexOf('.') + 1);
		String pkg = serviceName.substring(0, serviceName.lastIndexOf('.'));
		DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
			.setName("ExampleMessage")
			.addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
				.setName("json")
				.setNumber(1)
				.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
				.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
			.build();
		DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
			.setName(simple.toLowerCase() + ".proto")
			.setPackage(pkg)
			.addMessageType(message)
			.addService(DescriptorProtos.ServiceDescriptorProto.newBuilder()
				.setName(simple)
				.addMethod(DescriptorProtos.MethodDescriptorProto.newBuilder()
					.setName(method)
					.setInputType("." + pkg + ".ExampleMessage")
					.setOutputType("." + pkg + ".ExampleMessage")))
			.build();
		Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
		Descriptors.Descriptor messageType = descriptor.findMessageTypeByName("ExampleMessage");
		DynamicMessage response = DynamicMessage.newBuilder(messageType).setField(messageType.findFieldByName("json"), example).build();
		io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
			.setType(io.grpc.MethodDescriptor.MethodType.UNARY)
			.setFullMethodName(io.grpc.MethodDescriptor.generateFullMethodName(serviceName, method))
			.setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(messageType)))
			.setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(messageType)))
			.build();
		return ServerServiceDefinition.builder(serviceName)
			.addMethod(grpcMethod, ServerCalls.asyncUnaryCall((request, observer) -> {
				observer.onNext(response);
				observer.onCompleted();
			}))
			.build();
	}

	private static String methodName(ApiRequest request) {
		String url = request.getUrl() == null ? "" : request.getUrl();
		int slash = url.lastIndexOf('/');
		if (slash >= 0 && slash < url.length() - 1) {
			String name = url.substring(slash + 1);
			int query = name.indexOf('?');
			return query < 0 ? name : name.substring(0, query);
		}
		return request.getName() == null || request.getName().isBlank() ? "Call" : request.getName().replaceAll("[^A-Za-z0-9_]", "");
	}

}
