package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtoCodecTest {

	private static final String PROTO = """
			syntax = "proto3";
			package echo;

			message EchoRequest {
			  string message = 1;
			}

			message EchoResponse {
			  string message = 1;
			}

			service Echo {
			  rpc SayHello(EchoRequest) returns (EchoResponse);
			}
			""";

	@Test
	void encodesAndDecodesJsonPayload() throws Exception {
		byte[] encoded = ProtoCodec.encode(PROTO, "echo.Echo/SayHello", "{\"message\":\"hello\"}");
		assertTrue(encoded.length > 0);
		String decoded = ProtoCodec.decode(PROTO, "echo.Echo/SayHello", encoded, true);
		assertTrue(decoded.contains("hello"));
	}

	@Test
	void listsMethodsFromProto() throws Exception {
		assertFalse(ProtoCodec.listMethods(PROTO).isEmpty());
	}

}
