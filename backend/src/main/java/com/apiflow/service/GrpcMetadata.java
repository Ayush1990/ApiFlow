package com.apiflow.service;

import java.util.List;
import java.util.Locale;

import com.apiflow.model.KeyValue;

import io.grpc.Metadata;

public final class GrpcMetadata {

	private GrpcMetadata() {
	}

	public static Metadata fromHeaders(List<KeyValue> headers) {
		Metadata metadata = new Metadata();
		if (headers == null) {
			return metadata;
		}
		for (KeyValue header : headers) {
			if (header == null || !header.isEnabled() || header.getKey() == null || header.getKey().isBlank()) {
				continue;
			}
			String key = header.getKey().trim();
			if (key.startsWith(":") || "content-length".equalsIgnoreCase(key) || "host".equalsIgnoreCase(key)) {
				continue;
			}
			String value = header.getValue() == null ? "" : header.getValue();
			if ("authorization".equalsIgnoreCase(key) || key.toLowerCase(Locale.ROOT).startsWith("grpc-") || isCustomMetadata(key)) {
				metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
			}
			else {
				metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
			}
		}
		return metadata;
	}

	private static boolean isCustomMetadata(String key) {
		return key.contains("-") && !key.equalsIgnoreCase("user-agent") && !key.equalsIgnoreCase("accept");
	}

}
