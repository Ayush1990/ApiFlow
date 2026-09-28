package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TagExpressionTest {

	@Test
	void supportsTagCallsAndBooleanOperators() {
		List<String> tags = List.of("smoke", "regression");
		assertTrue(TagExpression.matches(tags, "@tag(smoke)"));
		assertTrue(TagExpression.matches(tags, "smoke && regression"));
		assertTrue(TagExpression.matches(tags, "smoke || health"));
		assertFalse(TagExpression.matches(tags, "health"));
	}

}
