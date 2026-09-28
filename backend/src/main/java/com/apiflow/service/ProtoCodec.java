package com.apiflow.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

public final class ProtoCodec {

	private static final Map<String, FileDescriptor> CACHE = new ConcurrentHashMap<>();
	private static final Pattern MESSAGE = Pattern.compile("message\\s+(\\w+)\\s*\\{([^}]*)}", Pattern.DOTALL);
	private static final Pattern FIELD = Pattern.compile("(optional|required|repeated)?\\s*(\\w+(?:\\.\\w+)?)\\s+(\\w+)\\s*=\\s*(\\d+)\\s*;");
	private static final Pattern SERVICE = Pattern.compile("service\\s+(\\w+)\\s*\\{([^}]*)}", Pattern.DOTALL);
	private static final Pattern RPC = Pattern.compile("rpc\\s+(\\w+)\\s*\\((\\w+)\\)\\s*returns\\s*\\((\\w+)\\)");

	private ProtoCodec() {
	}

	public static byte[] encode(String protoText, String fullMethod, String jsonBody) throws Exception {
		MethodPair pair = resolveMethod(protoText, fullMethod);
		if (pair == null || pair.input() == null) {
			return jsonBody == null || jsonBody.isBlank() ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
		}
		DynamicMessage.Builder builder = DynamicMessage.newBuilder(pair.input());
		if (jsonBody != null && !jsonBody.isBlank()) {
			JsonFormat.parser().ignoringUnknownFields().merge(jsonBody, builder);
		}
		return builder.build().toByteArray();
	}

	public static String decode(String protoText, String fullMethod, byte[] bytes, boolean response) throws Exception {
		if (bytes == null || bytes.length == 0) {
			return "";
		}
		MethodPair pair = resolveMethod(protoText, fullMethod);
		Descriptor descriptor = response ? (pair == null ? null : pair.output()) : (pair == null ? null : pair.input());
		if (descriptor == null) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
		DynamicMessage message = DynamicMessage.parseFrom(descriptor, bytes);
		return JsonFormat.printer().print(message);
	}

	public static List<String> listMethods(String protoText) throws Exception {
		FileDescriptor file = descriptor(protoText);
		List<String> methods = new ArrayList<>();
		for (Descriptors.ServiceDescriptor service : file.getServices()) {
			for (MethodDescriptor method : service.getMethods()) {
				methods.add(service.getFullName() + "/" + method.getName());
			}
		}
		return methods;
	}

	private static MethodPair resolveMethod(String protoText, String fullMethod) throws Exception {
		if (protoText == null || protoText.isBlank() || fullMethod == null || fullMethod.isBlank()) {
			return null;
		}
		int slash = fullMethod.lastIndexOf('/');
		if (slash <= 0) {
			return null;
		}
		String serviceName = fullMethod.substring(0, slash);
		String methodName = fullMethod.substring(slash + 1);
		FileDescriptor file = descriptor(protoText);
		Descriptors.ServiceDescriptor service = file.findServiceByName(serviceName.contains(".") ? serviceName.substring(serviceName.lastIndexOf('.') + 1) : serviceName);
		if (service == null) {
			for (Descriptors.ServiceDescriptor candidate : file.getServices()) {
				if (candidate.getFullName().equals(serviceName) || candidate.getName().equals(serviceName)) {
					service = candidate;
					break;
				}
			}
		}
		if (service == null) {
			return null;
		}
		MethodDescriptor method = service.findMethodByName(methodName);
		if (method == null) {
			return null;
		}
		return new MethodPair(method.getInputType(), method.getOutputType());
	}

	private static FileDescriptor descriptor(String protoText) throws Exception {
		String key = Integer.toHexString(protoText.hashCode());
		FileDescriptor cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		FileDescriptor compiled = compile(protoText);
		CACHE.put(key, compiled);
		return compiled;
	}

	private static FileDescriptor compile(String protoText) throws Exception {
		try {
			return compileWithProtoc(protoText);
		}
		catch (Exception ignored) {
			return compileInline(protoText);
		}
	}

	private static FileDescriptor compileWithProtoc(String protoText) throws Exception {
		Path dir = Files.createTempDirectory("apiflow-proto");
		Path proto = dir.resolve("request.proto");
		Files.writeString(proto, protoText);
		Path out = dir.resolve("descriptor.pb");
		Process process = new ProcessBuilder("protoc", "--descriptor_set_out=" + out, "--proto_path=" + dir, proto.getFileName().toString())
			.directory(dir.toFile())
			.redirectErrorStream(true)
			.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.waitFor() != 0 || !Files.exists(out)) {
			throw new IllegalArgumentException(output.isBlank() ? "protoc failed" : output);
		}
		com.google.protobuf.DescriptorProtos.FileDescriptorSet set = com.google.protobuf.DescriptorProtos.FileDescriptorSet.parseFrom(Files.readAllBytes(out));
		if (set.getFileCount() == 0) {
			throw new IllegalArgumentException("protoc returned no descriptors");
		}
		return FileDescriptor.buildFrom(set.getFile(0), new FileDescriptor[0]);
	}

	private static FileDescriptor compileInline(String protoText) throws Exception {
		String packageName = "";
		Matcher packageMatcher = Pattern.compile("package\\s+([\\w.]+)\\s*;").matcher(protoText);
		if (packageMatcher.find()) {
			packageName = packageMatcher.group(1);
		}
		Map<String, List<FieldSpec>> messages = new LinkedHashMap<>();
		Matcher messageMatcher = MESSAGE.matcher(protoText);
		while (messageMatcher.find()) {
			String name = messageMatcher.group(1);
			String body = messageMatcher.group(2);
			List<FieldSpec> fields = new ArrayList<>();
			Matcher fieldMatcher = FIELD.matcher(body);
			while (fieldMatcher.find()) {
				boolean repeated = "repeated".equals(fieldMatcher.group(1));
				fields.add(new FieldSpec(fieldMatcher.group(3), fieldMatcher.group(2), Integer.parseInt(fieldMatcher.group(4)), repeated));
			}
			messages.put(name, fields);
		}
		com.google.protobuf.DescriptorProtos.FileDescriptorProto.Builder file = com.google.protobuf.DescriptorProtos.FileDescriptorProto.newBuilder()
			.setName("dynamic.proto")
			.setSyntax("proto3");
		if (!packageName.isBlank()) {
			file.setPackage(packageName);
		}
		for (Map.Entry<String, List<FieldSpec>> entry : messages.entrySet()) {
			file.addMessageType(buildMessage(packageName, entry.getKey(), entry.getValue(), messages));
		}
		Matcher serviceMatcher = SERVICE.matcher(protoText);
		while (serviceMatcher.find()) {
			String serviceName = serviceMatcher.group(1);
			com.google.protobuf.DescriptorProtos.ServiceDescriptorProto.Builder service = com.google.protobuf.DescriptorProtos.ServiceDescriptorProto.newBuilder()
				.setName(serviceName);
			Matcher rpcMatcher = RPC.matcher(serviceMatcher.group(2));
			while (rpcMatcher.find()) {
				service.addMethod(com.google.protobuf.DescriptorProtos.MethodDescriptorProto.newBuilder()
					.setName(rpcMatcher.group(1))
					.setInputType("." + qualified(packageName, rpcMatcher.group(2)))
					.setOutputType("." + qualified(packageName, rpcMatcher.group(3)))
					.build());
			}
			file.addService(service.build());
		}
		return FileDescriptor.buildFrom(file.build(), new FileDescriptor[0]);
	}

	private static com.google.protobuf.DescriptorProtos.DescriptorProto buildMessage(String packageName, String name, List<FieldSpec> fields, Map<String, List<FieldSpec>> allMessages) {
		com.google.protobuf.DescriptorProtos.DescriptorProto.Builder message = com.google.protobuf.DescriptorProtos.DescriptorProto.newBuilder().setName(name);
		for (FieldSpec field : fields) {
			com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Builder protoField = com.google.protobuf.DescriptorProtos.FieldDescriptorProto.newBuilder()
				.setName(field.name())
				.setNumber(field.number())
				.setLabel(field.repeated() ? com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED : com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL);
			if (allMessages.containsKey(field.type())) {
				protoField.setType(com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
					.setTypeName("." + qualified(packageName, field.type()));
			}
			else {
				protoField.setType(mapScalar(field.type()));
			}
			message.addField(protoField.build());
		}
		return message.build();
	}

	private static com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type mapScalar(String type) {
		return switch (type) {
			case "string" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
			case "bool" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL;
			case "bytes" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES;
			case "double" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE;
			case "float" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT;
			case "int32", "sint32", "sfixed32" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32;
			case "int64", "sint64", "sfixed64" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64;
			case "uint32", "fixed32" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32;
			case "uint64", "fixed64" -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64;
			default -> com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
		};
	}

	private static String qualified(String packageName, String type) {
		return packageName == null || packageName.isBlank() ? type : packageName + "." + type;
	}

	public static String previewEncode(String protoText, String fullMethod, String jsonBody) throws Exception {
		byte[] bytes = encode(protoText, fullMethod, jsonBody);
		return bytes.length + " bytes: " + toHex(bytes, Math.min(bytes.length, 64));
	}

	private static String toHex(byte[] bytes, int limit) {
		StringBuilder out = new StringBuilder();
		for (int index = 0; index < limit; index++) {
			out.append(String.format("%02x", bytes[index]));
		}
		if (bytes.length > limit) {
			out.append("...");
		}
		return out.toString();
	}

	public static DynamicMessage parseMessage(String protoText, String messageName, byte[] bytes) throws Exception {
		Descriptor descriptor = descriptor(protoText).findMessageTypeByName(messageName);
		if (descriptor == null) {
			throw new IllegalArgumentException("Message not found: " + messageName);
		}
		return DynamicMessage.parseFrom(descriptor, bytes);
	}

	public static String messageFromStream(String protoText, String fullMethod, byte[] bytes) throws Exception {
		return decode(protoText, fullMethod, bytes, true);
	}

	public static byte[] fromJsonMessage(String protoText, String messageName, String json) throws Exception {
		Descriptor descriptor = descriptor(protoText).findMessageTypeByName(messageName);
		if (descriptor == null) {
			throw new IllegalArgumentException("Message not found: " + messageName);
		}
		DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
		if (json != null && !json.isBlank()) {
			JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
		}
		return builder.build().toByteArray();
	}

	public static String toJsonMessage(String protoText, String messageName, byte[] bytes) throws Exception {
		Descriptor descriptor = descriptor(protoText).findMessageTypeByName(messageName);
		if (descriptor == null) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
		return JsonFormat.printer().print(DynamicMessage.parseFrom(descriptor, new ByteArrayInputStream(bytes)));
	}

	private record FieldSpec(String name, String type, int number, boolean repeated) {
	}

	private record MethodPair(Descriptor input, Descriptor output) {
	}

}
