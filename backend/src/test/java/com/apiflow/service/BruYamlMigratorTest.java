package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.apiflow.model.ApiRequest;

class BruYamlMigratorTest {

	@Test
	void convertsYamlV3ToBruBlocks() {
		String yaml = """
				meta:
				  name: Ping
				  type: http
				method: get
				url: https://example.com/ping
				""";
		String bru = BruYamlMigrator.toBru(yaml);
		assertTrue(bru.contains("meta {"));
		assertTrue(bru.contains("name: Ping"));
		ApiRequest request = BrunoParser.request(yaml);
		assertEquals("Ping", request.getName());
		assertEquals("GET", request.getMethod());
	}

}
