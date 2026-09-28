package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.apiflow.model.CheckResult;
import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.KeyValue;
import com.apiflow.store.FileStore;

public final class ScriptRunner {

	private static volatile ScriptBridge bridge;

	private ScriptRunner() {
	}

	public static void setBridge(ScriptBridge scriptBridge) {
		bridge = scriptBridge;
	}

	public static void run(String script, Map<String, String> variables, ExecuteCommand command) {
		run(script, variables, command, null, "");
	}

	public static void run(String script, Map<String, String> variables, ExecuteCommand command, FileStore store, String collectionId) {
		run(script, variables, command, store, collectionId, "developer");
	}

	public static void run(String script, Map<String, String> variables, ExecuteCommand command, FileStore store, String collectionId, String scriptMode) {
		if (script == null || script.isBlank()) {
			return;
		}
		ScriptScope.begin(scriptMode);
		try {
			ScriptApi api = new ScriptApi(variables, command, developerMode(scriptMode));
			eval(PostmanScriptTranslator.translate(script), api, true, store, collectionId, scriptMode);
		}
		finally {
			ScriptScope.clear();
		}
	}

	public static ResponseApi runResponse(String script, Map<String, String> variables, ExecuteResult result) {
		return runResponse(script, variables, result, null, "");
	}

	public static ResponseApi runResponse(String script, Map<String, String> variables, ExecuteResult result, FileStore store, String collectionId) {
		return runResponse(script, variables, result, store, collectionId, "developer");
	}

	public static ResponseApi runResponse(String script, Map<String, String> variables, ExecuteResult result, FileStore store, String collectionId, String scriptMode) {
		ResponseApi api = new ResponseApi(variables, result, developerMode(scriptMode));
		if (script == null || script.isBlank()) {
			return api;
		}
		ScriptScope.begin(scriptMode);
		try {
			eval(PostmanScriptTranslator.translate(script), api, false, store, collectionId, scriptMode);
		}
		finally {
			ScriptScope.clear();
		}
		return api;
	}

	public static String preRequestScriptToRun(String script) {
		if (script == null || script.isBlank() || shouldDeferToPostResponse(script)) {
			return "";
		}
		return script;
	}

	public static String mergedPostResponseScript(String postResponseScript, String preRequestScript) {
		String post = postResponseScript == null ? "" : postResponseScript;
		if (preRequestScript == null || preRequestScript.isBlank() || !shouldDeferToPostResponse(preRequestScript)) {
			return post;
		}
		if (post.isBlank()) {
			return preRequestScript;
		}
		return post + "\n" + preRequestScript;
	}

	public static boolean shouldDeferToPostResponse(String script) {
		if (script == null || script.isBlank()) {
			return false;
		}
		String lower = script.toLowerCase(Locale.ROOT);
		boolean usesResponse = lower.contains("pm.visualizer")
			|| lower.contains("pm.response")
			|| lower.contains("getbody()")
			|| lower.contains("getstatus()")
			|| lower.contains("getheader(")
			|| lower.contains("jsonpath(")
			|| lower.contains("pm.test(")
			|| lower.contains("pm.expect(")
			|| lower.contains("setvisualizer(");
		if (!usesResponse) {
			return false;
		}
		boolean modifiesRequest = lower.contains("setheader(")
			|| lower.contains("seturl(")
			|| lower.contains("setmethod(")
			|| lower.contains("setbody(")
			|| lower.contains("apiflow.setheader")
			|| lower.contains("apiflow.seturl")
			|| lower.contains("apiflow.setmethod")
			|| lower.contains("apiflow.setbody");
		return !modifiesRequest;
	}

	private static boolean developerMode(String scriptMode) {
		return scriptMode == null || !"safe".equalsIgnoreCase(scriptMode.trim());
	}

	private static void eval(String script, Object api, boolean request, FileStore store, String collectionId) {
		eval(script, api, request, store, collectionId, "developer");
	}

	private static void eval(String script, Object api, boolean request, FileStore store, String collectionId, String scriptMode) {
		boolean developer = developerMode(scriptMode);
		HostAccess access = HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).build();
		try (Context context = Context.newBuilder("js")
			.allowHostAccess(access)
			.option("engine.WarnInterpreterOnly", "false")
			.build()) {
			Value bindings = context.getBindings("js");
			bindings.putMember("apiflow", api);
			bindings.putMember("bru", api);
			ScriptDatasets.DatasetsApi datasetsApi = ScriptDatasets.api();
			bindings.putMember("pm", new ScriptDatasets.PostmanCompat(datasetsApi));
			bindings.putMember("datasets", datasetsApi);
			bindings.putMember("console", new ScriptConsole());
			ScriptRunnerApi.RunnerHandle runner = new ScriptRunnerApi.RunnerHandle();
			bindings.putMember("runner", runner);
			if (api instanceof ScriptApi scriptApi) {
				ExecuteCommand command = scriptApi.command();
				if (request && command != null) {
					bindings.putMember("req", new ScriptReq(command, scriptApi.variables()));
				}
				else {
					bindings.putMember("req", new ScriptHttp.SyncClient(scriptApi.variables()));
				}
			}
			if (store != null) {
				ScriptModuleLoader.install(context, store, collectionId, developer);
			}
			bindings.putMember("cookies", bridge == null ? new ScriptBridge.ScriptCookies(java.util.List.of()) : bridge.cookies());
			String helpers = developer ? """
					function getProcessEnv(name) { return apiflow.getProcessEnv(name); }
					function sleep(ms) { apiflow.sleep(ms); }
					""" : """
					function getProcessEnv(name) { return ''; }
					function sleep(ms) { throw new Error('sleep is disabled in safe script mode'); }
					""";
			context.eval("js", """
					function setVar(name, value) { apiflow.setVar(name, value); }
					function getVar(name) { return apiflow.getVar(name); }
					function setEnvVar(name, value) { apiflow.setEnvVar(name, value); }
					function getEnvVar(name) { return apiflow.getEnvVar(name); }
					function setCollectionVar(name, value) { apiflow.setCollectionVar(name, value); }
					function getCollectionVar(name) { return apiflow.getCollectionVar(name); }
					""" + helpers + """
					function uuid() { return apiflow.uuid(); }
					function md5(value) { return apiflow.md5(value); }
					function sha256(value) { return apiflow.sha256(value); }
					function test(name, fn) { apiflow.test(name, fn); }
					function expect(value) {
						const raw = apiflow.expect(value);
						const chain = (negate) => ({
							equal: (expected) => negate ? raw.toNotEqual(expected) : raw.toEqual(expected),
							eql: (expected) => negate ? raw.toNotEqual(expected) : raw.toEqual(expected),
							include: (expected) => negate ? raw.toNotInclude(expected) : raw.toInclude(expected),
							contain: (expected) => negate ? raw.toNotInclude(expected) : raw.toContain(expected),
							match: (pattern) => negate ? raw.toNotMatch(pattern) : raw.toMatch(pattern),
							be: {
								ok: () => negate ? raw.toBeFalsy() : raw.toBeTruthy(),
								true: () => negate ? raw.toNotEqual(true) : raw.toEqual(true),
								false: () => negate ? raw.toNotEqual(false) : raw.toEqual(false),
								above: (number) => negate ? raw.toBeAtMost(number) : raw.toBeGreaterThan(number),
								below: (number) => negate ? raw.toBeAtLeast(number) : raw.toBeLessThan(number),
								a: (type) => negate ? raw.toNotBe(type) : raw.toBe(type),
							},
							have: { length: (number) => negate ? raw.toNotHaveLength(number) : raw.toHaveLength(number) },
						});
						const positive = chain(false);
						return { to: positive, not: { to: chain(true) }, toEqual: positive.equal, toBe: (expected) => raw.toBe(expected), toInclude: positive.include, toBeTruthy: () => raw.toBeTruthy(), toBeFalsy: () => raw.toBeFalsy() };
					}
					function not(value) { return apiflow.not(value); }
					if (typeof pm !== 'undefined' && pm) {
						const datasetsApi = pm.getDatasets ? pm.getDatasets() : pm.datasets;
						const read = (name) => {
							try { return apiflow.getEnvVar(name) || apiflow.getCollectionVar(name) || apiflow.getVar(name) || ''; }
							catch (error) { return ''; }
						};
						const responseText = () => { try { return apiflow.getBody(); } catch (error) { return ''; } };
						pm = {
							visualizer: { set: (template, data) => apiflow.setVisualizer(String(template || ''), JSON.stringify(data ?? {})) },
							datasets: datasetsApi,
							test: (name, fn) => test(name, fn),
							expect,
							request: {
								method: (() => { try { return apiflow.getMethod(); } catch (error) { return ''; } })(),
								url: { toString: () => { try { return apiflow.getUrl(); } catch (error) { return ''; } } },
								headers: { get: (name) => { try { return apiflow.getRequestHeader(name); } catch (error) { return ''; } }, add: (name, value) => { try { apiflow.setHeader(name, value); } catch (error) {} } },
							},
							response: {
								get code() { try { return apiflow.getStatus(); } catch (error) { return 0; } },
								text: responseText,
								json: () => JSON.parse(responseText() || 'null'),
								headers: { get: (name) => { try { return apiflow.getHeader(name); } catch (error) { return ''; } } },
							},
							environment: { get: (name) => { try { return apiflow.getEnvVar(name); } catch (error) { return ''; } }, set: (name, value) => { try { apiflow.setEnvVar(name, value); } catch (error) {} } },
							collectionVariables: { get: (name) => { try { return apiflow.getCollectionVar(name); } catch (error) { return ''; } }, set: (name, value) => { try { apiflow.setCollectionVar(name, value); } catch (error) {} } },
							globals: { get: (name) => { try { return apiflow.getVar(name); } catch (error) { return ''; } }, set: (name, value) => { try { apiflow.setVar(name, value); } catch (error) {} } },
							variables: { get: read, set: (name, value) => { try { apiflow.setVar(name, value); } catch (error) {} }, replaceIn: (value) => { try { return apiflow.interpolate(value); } catch (error) { return value; } } },
							info: { requestName: (() => { try { return apiflow.getRequestName(); } catch (error) { return ''; } })(), eventName: '%s' },
							iterationData: { get: (name) => { try { return runner.getIterationData().get(name); } catch (error) { return undefined; } } },
							sendRequest: (target) => { try { return apiflow.runRequest('', typeof target === 'string' ? target : (target && target.url) || ''); } catch (error) { throw error; } },
							cookies,
						};
					}
					""".formatted(request ? "prerequest" : "test"));
			if (request) {
				String runnerHelpers = developer ? """
						bru.runRequest = function(collection, request) { return apiflow.runRequest(collection, request); };
						""" : """
						bru.runRequest = function() { throw new Error('bru.runRequest is disabled in safe script mode'); };
						""";
				context.eval("js", """
						bru.getRunner = function() { return runner; };
						bru.getIterationData = function() { return runner.getIterationData(); };
						bru.getIterationIndex = function() { return runner.getIterationIndex(); };
						bru.getTotalIterations = function() { return runner.getTotalIterations(); };
						bru.setNextRequest = function(name) { runner.setNextRequest(name); };
						bru.cookies = cookies;
						""" + runnerHelpers);
			}
			context.eval("js", """
					function getStatus() { return apiflow.getStatus(); }
					function getBody() { return apiflow.getBody(); }
					function getHeader(name) { return apiflow.getHeader(name); }
					function jsonPath(path) { return apiflow.jsonPath(path); }
					""");
			if (!request) {
				context.eval("js", """
						var res = apiflow;
						res.query = function() { return apiflow.query(); };
						""");
			}
			context.eval("js", ScriptLibrary.source() + "\n" + script);
		}
		catch (PolyglotException ex) {
			throw new IllegalArgumentException("Script error: " + ex.getMessage());
		}
	}

	public static class ScriptApi {

		private final Map<String, String> variables;
		private final ExecuteCommand command;
		private final boolean developer;
		private final java.util.List<CheckResult> scriptChecks = new java.util.ArrayList<>();

		ScriptApi(Map<String, String> variables, ExecuteCommand command) {
			this(variables, command, true);
		}

		ScriptApi(Map<String, String> variables, ExecuteCommand command, boolean developer) {
			this.variables = variables;
			this.command = command;
			this.developer = developer;
		}

		Map<String, String> variables() {
			return variables;
		}

		ExecuteCommand command() {
			return command;
		}

		@HostAccess.Export
		public void setVar(String name, Object value) {
			if (name != null && !name.isBlank()) {
				variables.put(name.trim(), value == null ? "" : String.valueOf(value));
			}
		}

		@HostAccess.Export
		public void setEnvVar(String name, Object value) {
			if (name != null && !name.isBlank()) {
				ScriptScope.State scope = ScriptScope.current();
				if (scope != null) {
					scope.markEnv(name.trim());
				}
				variables.put(name.trim(), value == null ? "" : String.valueOf(value));
			}
		}

		@HostAccess.Export
		public void setCollectionVar(String name, Object value) {
			if (name != null && !name.isBlank()) {
				ScriptScope.State scope = ScriptScope.current();
				if (scope != null) {
					scope.markCollection(name.trim());
				}
				variables.put(name.trim(), value == null ? "" : String.valueOf(value));
			}
		}

		@HostAccess.Export
		public String getVar(String name) {
			return variables.getOrDefault(name, "");
		}

		@HostAccess.Export
		public String getEnvVar(String name) {
			return getVar(name);
		}

		@HostAccess.Export
		public String getCollectionVar(String name) {
			return getVar(name);
		}

		@HostAccess.Export
		public String interpolate(String input) {
			return VariableResolver.interpolate(input == null ? "" : input, variables);
		}

		@HostAccess.Export
		public String getRequestName() {
			return command == null || command.getRequestName() == null ? "" : command.getRequestName();
		}

		@HostAccess.Export
		public void setUrl(String url) {
			command.setUrl(url == null ? "" : url);
		}

		@HostAccess.Export
		public String getUrl() {
			return command.getUrl() == null ? "" : command.getUrl();
		}

		@HostAccess.Export
		public void setMethod(String method) {
			command.setMethod(method == null ? "GET" : method);
		}

		@HostAccess.Export
		public void setBody(String body) {
			command.setBody(body == null ? "" : body);
		}

		@HostAccess.Export
		public void setHeader(String name, String value) {
			if (name == null || name.isBlank()) {
				return;
			}
			List<KeyValue> headers = command.getHeaders();
			for (KeyValue header : headers) {
				if (name.equalsIgnoreCase(header.getKey())) {
					header.setValue(value == null ? "" : value);
					header.setEnabled(true);
					return;
				}
			}
			headers.add(new KeyValue(name, value == null ? "" : value, true));
		}

		@HostAccess.Export
		public int getStatus() {
			throw new IllegalStateException("Response is not available in pre-request script. Move this code to the After-response script.");
		}

		@HostAccess.Export
		public String getBody() {
			throw new IllegalStateException("Response is not available in pre-request script. Move this code to the After-response script.");
		}

		@HostAccess.Export
		public String getHeader(String name) {
			throw new IllegalStateException("Response is not available in pre-request script. Move this code to the After-response script.");
		}

		@HostAccess.Export
		public void setVisualizer(String template, String dataJson) {
			throw new IllegalStateException("pm.visualizer is only available in the After-response script.");
		}

		@HostAccess.Export
		public String uuid() {
			return UUID.randomUUID().toString();
		}

		@HostAccess.Export
		public String md5(String value) {
			return hash("MD5", value);
		}

		@HostAccess.Export
		public String sha256(String value) {
			return hash("SHA-256", value);
		}

		@HostAccess.Export
		public void test(String name, Value fn) {
			try {
				if (fn != null && fn.canExecute()) {
					fn.executeVoid();
				}
				scriptChecks.add(new CheckResult("test", true, name == null || name.isBlank() ? "Test passed" : name));
			}
			catch (Exception ex) {
				scriptChecks.add(new CheckResult("test", false, (name == null ? "Test" : name) + ": " + ex.getMessage()));
			}
		}

		@HostAccess.Export
		public Expect expect(Object value) {
			return new Expect(value);
		}

		@HostAccess.Export
		public NotExpect not(Object value) {
			return new NotExpect(value);
		}

		@HostAccess.Export
		public ScriptHttp.SyncResponse request(String method, String url, String body) {
			if (!developer) {
				throw new IllegalArgumentException("Outbound HTTP from scripts is disabled in safe script mode");
			}
			return new ScriptHttp.SyncClient(variables).request(method, url, body, java.util.Map.of());
		}

		@HostAccess.Export
		public void sleep(long ms) {
			if (!developer) {
				throw new IllegalArgumentException("sleep is disabled in safe script mode");
			}
			try {
				Thread.sleep(Math.max(0, ms));
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

		@HostAccess.Export
		public void skip() {
			RunnerContext.State state = RunnerContext.current();
			if (state != null) {
				state.setSkipCurrent(true);
			}
		}

		@HostAccess.Export
		public ScriptRunnerApi.RunnerHandle getRunner() {
			return new ScriptRunnerApi.RunnerHandle();
		}

		@HostAccess.Export
		public String getProcessEnv(String name) {
			if (!developer) {
				return "";
			}
			return bridge == null ? System.getenv(name == null ? "" : name.trim()) : bridge.getProcessEnv(name);
		}

		@HostAccess.Export
		public ExecuteResult runRequest(String collection, String request) {
			if (!developer) {
				throw new IllegalArgumentException("bru.runRequest is disabled in safe script mode");
			}
			if (bridge == null) {
				throw new IllegalArgumentException("bru.runRequest is unavailable");
			}
			return bridge.runRequest(collection, request);
		}

		@HostAccess.Export
		public ScriptRunnerApi.IterationData getIterationData() {
			return new ScriptRunnerApi.IterationData();
		}

		@HostAccess.Export
		public int getIterationIndex() {
			RunnerContext.State state = RunnerContext.current();
			return state == null ? 0 : state.getIterationIndex();
		}

		@HostAccess.Export
		public int getTotalIterations() {
			RunnerContext.State state = RunnerContext.current();
			return state == null ? 1 : state.getTotalIterations();
		}

		@HostAccess.Export
		public void setNextRequest(String name) {
			RunnerContext.State state = RunnerContext.current();
			if (state != null) {
				state.setNextRequest(name == null ? "" : name);
			}
		}

		@HostAccess.Export
		public String getMethod() {
			return command.getMethod() == null ? "GET" : command.getMethod();
		}

		@HostAccess.Export
		public void deleteHeader(String name) {
			if (name == null || name.isBlank()) {
				return;
			}
			command.getHeaders().removeIf(header -> name.equalsIgnoreCase(header.getKey()));
		}

		@HostAccess.Export
		public String getRequestHeader(String name) {
			if (command == null || name == null) {
				return "";
			}
			return command.getHeaders().stream().filter(header -> name.equalsIgnoreCase(header.getKey())).map(com.apiflow.model.KeyValue::getValue).findFirst().orElse("");
		}

		public java.util.List<CheckResult> getScriptChecks() {
			return scriptChecks;
		}

		private static String hash(String algorithm, String value) {
			try {
				byte[] bytes = MessageDigest.getInstance(algorithm).digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
				return HexFormat.of().formatHex(bytes);
			}
			catch (Exception ex) {
				throw new IllegalArgumentException(algorithm + " failed");
			}
		}
	}

	public static class Expect {

		private final Object value;

		Expect(Object value) {
			this.value = value;
		}

		@HostAccess.Export
		public void toBe(Object expected) {
			String left = value == null ? "" : String.valueOf(value);
			String right = expected == null ? "" : String.valueOf(expected);
			if (!left.equals(right)) {
				throw new IllegalArgumentException("Expected " + right + " but got " + left);
			}
		}

		@HostAccess.Export
		public void toEqual(Object expected) {
			toBe(expected);
		}

		@HostAccess.Export
		public void toNotEqual(Object expected) {
			String left = value == null ? "" : String.valueOf(value);
			String right = expected == null ? "" : String.valueOf(expected);
			if (left.equals(right)) {
				throw new IllegalArgumentException("Expected " + left + " not to equal " + right);
			}
		}

		@HostAccess.Export
		public void toNotBe(Object expected) {
			toNotEqual(expected);
		}

		@HostAccess.Export
		public void toNotInclude(Object expected) {
			String left = value == null ? "" : String.valueOf(value);
			String right = expected == null ? "" : String.valueOf(expected);
			if (left.contains(right)) {
				throw new IllegalArgumentException("Expected not to include " + right);
			}
		}

		@HostAccess.Export
		public void toNotMatch(String pattern) {
			String left = value == null ? "" : String.valueOf(value);
			if (pattern != null && left.matches(pattern)) {
				throw new IllegalArgumentException("Expected not to match /" + pattern + "/");
			}
		}

		@HostAccess.Export
		public void toBeAtMost(double expected) {
			if (toNumber(value) > expected) {
				throw new IllegalArgumentException("Expected value <= " + expected);
			}
		}

		@HostAccess.Export
		public void toBeAtLeast(double expected) {
			if (toNumber(value) < expected) {
				throw new IllegalArgumentException("Expected value >= " + expected);
			}
		}

		@HostAccess.Export
		public void toNotHaveLength(int expected) {
			int length = value == null ? 0 : String.valueOf(value).length();
			if (length == expected) {
				throw new IllegalArgumentException("Expected length not to be " + expected);
			}
		}

		@HostAccess.Export
		public void toInclude(Object expected) {
			String left = value == null ? "" : String.valueOf(value);
			String right = expected == null ? "" : String.valueOf(expected);
			if (!left.contains(right)) {
				throw new IllegalArgumentException("Expected to include " + right);
			}
		}

		@HostAccess.Export
		public void toBeTruthy() {
			if (value == null || "false".equals(String.valueOf(value)) || "0".equals(String.valueOf(value)) || String.valueOf(value).isBlank()) {
				throw new IllegalArgumentException("Expected truthy value");
			}
		}

		@HostAccess.Export
		public void toBeFalsy() {
			if (value != null && !"false".equals(String.valueOf(value)) && !"0".equals(String.valueOf(value)) && !String.valueOf(value).isBlank()) {
				throw new IllegalArgumentException("Expected falsy value");
			}
		}

		@HostAccess.Export
		public void toMatch(String pattern) {
			String left = value == null ? "" : String.valueOf(value);
			if (pattern == null || !left.matches(pattern)) {
				throw new IllegalArgumentException("Expected to match /" + pattern + "/");
			}
		}

		@HostAccess.Export
		public void toBeGreaterThan(double expected) {
			double actual = toNumber(value);
			if (actual <= expected) {
				throw new IllegalArgumentException("Expected " + actual + " > " + expected);
			}
		}

		@HostAccess.Export
		public void toBeLessThan(double expected) {
			double actual = toNumber(value);
			if (actual >= expected) {
				throw new IllegalArgumentException("Expected " + actual + " < " + expected);
			}
		}

		@HostAccess.Export
		public void toBeDefined() {
			if (value == null) {
				throw new IllegalArgumentException("Expected defined value");
			}
		}

		@HostAccess.Export
		public void toBeUndefined() {
			if (value != null) {
				throw new IllegalArgumentException("Expected undefined value");
			}
		}

		@HostAccess.Export
		public void toHaveLength(int expected) {
			int length = value == null ? 0 : String.valueOf(value).length();
			if (length != expected) {
				throw new IllegalArgumentException("Expected length " + expected + " but got " + length);
			}
		}

		@HostAccess.Export
		public void toContain(Object expected) {
			toInclude(expected);
		}

		private static double toNumber(Object value) {
			try {
				return Double.parseDouble(String.valueOf(value));
			}
			catch (Exception ex) {
				return Double.NaN;
			}
		}
	}

	public static class NotExpect {

		private final Object value;

		NotExpect(Object value) {
			this.value = value;
		}

		@HostAccess.Export
		public void toBe(Object expected) {
			String left = value == null ? "" : String.valueOf(value);
			String right = expected == null ? "" : String.valueOf(expected);
			if (left.equals(right)) {
				throw new IllegalArgumentException("Expected not " + right);
			}
		}

		@HostAccess.Export
		public void toEqual(Object expected) {
			toBe(expected);
		}
	}

	public static class ScriptConsole {
		@HostAccess.Export
		public void log(Object value) {
			ScriptScope.State state = ScriptScope.current();
			if (state != null) {
				state.log(String.valueOf(value));
			}
		}
	}

	public static class ResponseApi extends ScriptApi {

		private final ExecuteResult result;

		ResponseApi(Map<String, String> variables, ExecuteResult result) {
			this(variables, result, true);
		}

		ResponseApi(Map<String, String> variables, ExecuteResult result, boolean developer) {
			super(variables, null, developer);
			this.result = result;
		}

		@HostAccess.Export
		public void setVisualizer(String template, String dataJson) {
			result.setVisualizerTemplate(template == null ? "" : template);
			result.setVisualizerData(dataJson == null ? "{}" : dataJson);
		}

		@HostAccess.Export
		@Override
		public void setVar(String name, Object value) {
			super.setVar(name, value);
		}

		@HostAccess.Export
		@Override
		public String getVar(String name) {
			return super.getVar(name);
		}

		@HostAccess.Export
		@Override
		public void setUrl(String url) {
		}

		@HostAccess.Export
		@Override
		public void setMethod(String method) {
		}

		@HostAccess.Export
		@Override
		public void setBody(String body) {
		}

		@HostAccess.Export
		@Override
		public void setHeader(String name, String value) {
		}

		@HostAccess.Export
		public int getStatus() {
			return result.getStatus();
		}

		@HostAccess.Export
		public String getBody() {
			return result.getBody() == null ? "" : result.getBody();
		}

		@HostAccess.Export
		public String getHeader(String name) {
			if (name == null) {
				return "";
			}
			for (KeyValue header : result.getHeaders()) {
				if (name.equalsIgnoreCase(header.getKey())) {
					return header.getValue() == null ? "" : header.getValue();
				}
			}
			return "";
		}

		@HostAccess.Export
		public String jsonPath(String path) {
			String value = Checks.read(result.getBody(), path);
			return value == null ? "" : value;
		}

		@HostAccess.Export
		public boolean hasBody() {
			return result.getBody() != null && !result.getBody().isBlank();
		}

		@HostAccess.Export
		public String getStatusText() {
			return result.getStatusText() == null ? "" : result.getStatusText();
		}

		@HostAccess.Export
		public long getResponseTime() {
			return result.getTimeMs();
		}

		@HostAccess.Export
		public long getSize() {
			return result.getSize();
		}

		@HostAccess.Export
		public java.util.List<java.util.Map<String, String>> getHeaders() {
			java.util.List<java.util.Map<String, String>> headers = new java.util.ArrayList<>();
			for (KeyValue header : result.getHeaders()) {
				if (header.getKey() != null) {
					headers.add(java.util.Map.of("name", header.getKey(), "value", header.getValue() == null ? "" : header.getValue()));
				}
			}
			return headers;
		}

		@HostAccess.Export
		public String getBodyAsJson() {
			return getBody();
		}

		@HostAccess.Export
		public ResponseQuery query() {
			return ResponseQuery.parse(getBody());
		}

	}

}
