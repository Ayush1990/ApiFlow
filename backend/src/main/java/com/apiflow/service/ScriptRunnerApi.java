package com.apiflow.service;

import java.util.Map;

import org.graalvm.polyglot.HostAccess;

public class ScriptRunnerApi {

	@HostAccess.Export
	public RunnerHandle getRunner() {
		return new RunnerHandle();
	}

	@HostAccess.Export
	public IterationData getIterationData() {
		return new IterationData();
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
	public ScriptHttp.SyncResponse sendRequest(Map<String, Object> options) {
		String method = options == null ? "GET" : String.valueOf(options.getOrDefault("method", "GET"));
		String url = options == null ? "" : String.valueOf(options.getOrDefault("url", ""));
		String body = options == null ? "" : String.valueOf(options.getOrDefault("body", ""));
		return new ScriptHttp.SyncClient(Map.of()).request(method, url, body, Map.of());
	}

	public static final class RunnerHandle {

		@HostAccess.Export
		public void setNextRequest(String name) {
			RunnerContext.State state = RunnerContext.current();
			if (state != null) {
				state.setNextRequest(name == null ? "" : name);
			}
		}

		@HostAccess.Export
		public void skipRequest() {
			RunnerContext.State state = RunnerContext.current();
			if (state != null) {
				state.setSkipCurrent(true);
			}
		}

		@HostAccess.Export
		public void stopExecution() {
			RunnerContext.State state = RunnerContext.current();
			if (state != null) {
				state.setStopRun(true);
			}
		}

		@HostAccess.Export
		public IterationData getIterationData() {
			return new IterationData();
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

	}

	public static final class IterationData {

		@HostAccess.Export
		public boolean has(String key) {
			RunnerContext.State state = RunnerContext.current();
			return state != null && state.hasIterationKey(key);
		}

		@HostAccess.Export
		public String get(String key) {
			RunnerContext.State state = RunnerContext.current();
			return state == null ? "" : state.getIterationValue(key);
		}

		@HostAccess.Export
		public Map<String, String> getAll() {
			RunnerContext.State state = RunnerContext.current();
			return state == null ? Map.of() : state.getIterationRow();
		}

		@HostAccess.Export
		public void unset(String key) {
			RunnerContext.State state = RunnerContext.current();
			if (state != null) {
				state.unsetIterationKey(key);
			}
		}

	}

}
