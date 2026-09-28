package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;

class ScriptRunnerTest {

	@Test
	void runsJavaScriptAndUpdatesTheRequest() {
		Map<String, String> variables = new HashMap<>();
		variables.put("baseUrl", "https://example.com");
		ExecuteCommand command = new ExecuteCommand();
		command.setUrl("{{baseUrl}}/old");
		ScriptRunner.run("""
				const token = "abc-" + getVar("baseUrl").length;
				setVar("token", token);
				apiflow.setUrl(getVar("baseUrl") + "/todos/1");
				apiflow.setHeader("X-Test", token);
				""", variables, command);
		assertEquals("abc-19", variables.get("token"));
		assertEquals("https://example.com/todos/1", command.getUrl());
		assertEquals("abc-19", command.getHeaders().get(0).getValue());
	}

	@Test
	void tracksEnvAndCollectionVarScopes() {
		Map<String, String> variables = new HashMap<>();
		ExecuteCommand command = new ExecuteCommand();
		ScriptRunner.run("""
				setEnvVar("envToken", "from-env");
				setCollectionVar("collectionToken", "from-collection");
				bru.interpolate("{{envToken}}");
				""", variables, command);
		assertEquals("from-env", variables.get("envToken"));
		assertEquals("from-collection", variables.get("collectionToken"));
	}

	@Test
	void visualizerScriptSetsTemplateAndData() {
		Map<String, String> variables = new HashMap<>();
		ExecuteResult result = new ExecuteResult();
		result.setStatus(200);
		result.setBody("{\"id\":1,\"title\":\"delectus aut autem\",\"completed\":false,\"userId\":1}");
		ScriptRunner.runResponse("""
				const body = pm.response.json();
				pm.visualizer.set('<h2>{{title}}</h2>', body);
				""", variables, result);
		assertTrue(result.getVisualizerTemplate().contains("{{title}}"));
		assertTrue(result.getVisualizerData().contains("delectus aut autem"));
	}

	@Test
	void defersResponseOnlyPreRequestScriptToPostResponse() {
		String script = "const body = pm.response.json();\npm.visualizer.set('<p>{{id}}</p>', body);";
		assertTrue(ScriptRunner.shouldDeferToPostResponse(script));
		assertEquals("", ScriptRunner.preRequestScriptToRun(script));
		assertEquals(script, ScriptRunner.mergedPostResponseScript("", script));
	}

	@Test
	void runnerSkipMarksContext() {
		Map<String, String> variables = new HashMap<>();
		ExecuteCommand command = new ExecuteCommand();
		RunnerContext.begin(0, 1, Map.of());
		try {
			ScriptRunner.run("bru.skip();", variables, command);
			assertTrue(RunnerContext.current().isSkipCurrent());
		}
		finally {
			RunnerContext.clear();
		}
	}

}
