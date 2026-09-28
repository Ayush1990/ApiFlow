package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.apiflow.model.KeyValue;

import io.grpc.Metadata;

class GrpcMetadataTest {

	@Test
	void convertsEnabledHeadersToMetadata() {
		Metadata metadata = GrpcMetadata.fromHeaders(java.util.List.of(
			new KeyValue("authorization", "Bearer abc", true),
			new KeyValue("x-custom", "value", true),
			new KeyValue("disabled", "skip", false)
		));
		assertEquals("Bearer abc", metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)));
		assertEquals("value", metadata.get(Metadata.Key.of("x-custom", Metadata.ASCII_STRING_MARSHALLER)));
	}

}
