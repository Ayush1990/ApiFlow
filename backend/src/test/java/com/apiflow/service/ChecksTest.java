package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.apiflow.model.Assertion;
import com.apiflow.model.CheckResult;
import com.apiflow.model.KeyValue;

class ChecksTest {

	@Test
	void headerTimeAndRegex() {
		Assertion header = new Assertion();
		header.setType("header");
		header.setPath("X-Request-Id");
		header.setExpected("abc");
		Assertion time = new Assertion();
		time.setType("time");
		time.setExpected("100");
		Assertion regex = new Assertion();
		regex.setType("regex");
		regex.setExpected("todo \\d+");
		List<CheckResult> results = Checks.evaluate(List.of(header, time, regex), 200, "todo 12", List.of(new KeyValue("X-Request-Id", "abc", true)), 40);
		assertTrue(results.get(0).isPassed());
		assertTrue(results.get(1).isPassed());
		assertTrue(results.get(2).isPassed());

		time.setExpected("10");
		assertFalse(Checks.evaluate(List.of(time), 200, "", List.of(), 40).get(0).isPassed());
		assertEquals("header", results.get(0).getType());
	}

}
