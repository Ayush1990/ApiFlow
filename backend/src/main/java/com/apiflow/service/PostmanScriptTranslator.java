package com.apiflow.service;

public final class PostmanScriptTranslator {

	private PostmanScriptTranslator() {
	}

	public static String translate(String script) {
		if (script == null || script.isBlank()) {
			return "";
		}
		String translated = script;
		translated = translated.replace("pm.environment.set(", "setVar(");
		translated = translated.replace("pm.collectionVariables.set(", "setVar(");
		translated = translated.replace("pm.variables.set(", "setVar(");
		translated = translated.replace("pm.environment.get(", "getVar(");
		translated = translated.replace("pm.collectionVariables.get(", "getVar(");
		translated = translated.replace("pm.variables.get(", "getVar(");
		translated = translated.replace("pm.response.code", "getStatus()");
		translated = translated.replace("pm.response.text()", "getBody()");
		translated = translated.replace("pm.response.json()", "JSON.parse(getBody())");
		translated = translated.replace("pm.test(", "test(");
		translated = translated.replace("pm.expect(", "expect(");
		translated = translated.replace("pm.sendRequest", "req.request");
		return translated;
	}

}
