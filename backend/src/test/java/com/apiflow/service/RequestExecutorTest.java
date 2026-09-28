package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RequestExecutorTest {

	@Test
	void replacesVariables() {
		String url = RequestExecutor.interpolate("{{baseUrl}}/todos/{{id}}", Map.of("baseUrl", "https://example.com", "id", "7"));
		assertEquals("https://example.com/todos/7", url);
	}

	@Test
	void leavesUnknownVariablesInPlace() {
		assertEquals("{{missing}}", RequestExecutor.interpolate("{{missing}}", Map.of()));
	}

}
