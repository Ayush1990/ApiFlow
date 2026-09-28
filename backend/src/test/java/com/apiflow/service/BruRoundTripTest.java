package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.apiflow.model.ApiRequest;

class BruRoundTripTest {

	@Test
	void exportsAndParsesHeadersTagsAndScripts() {
		ApiRequest request = new ApiRequest();
		request.setName("Health");
		request.setMethod("GET");
		request.setUrl("https://example.com/health");
		request.setTags(java.util.List.of("smoke", "health"));
		request.getHeaders().add(new com.apiflow.model.KeyValue("Accept", "application/json", true));
		request.setPreRequestScript("setVar('ready', 'yes');");
		request.setPostResponseScript("test('ok', () => expect(res.getStatus()).toBe(200));");
		String bru = ExportService.BrunoExporter.request(request, "");
		ApiRequest parsed = BrunoParser.request(bru);
		assertEquals("Health", parsed.getName());
		assertEquals("GET", parsed.getMethod());
		assertEquals("https://example.com/health", parsed.getUrl());
		assertTrue(parsed.getTags().contains("smoke"));
		assertEquals("application/json", parsed.getHeaders().get(0).getValue());
		assertTrue(parsed.getPreRequestScript().contains("ready"));
		assertTrue(parsed.getPostResponseScript().contains("expect"));
	}

}
