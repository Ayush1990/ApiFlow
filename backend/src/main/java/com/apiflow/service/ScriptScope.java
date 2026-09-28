package com.apiflow.service;

import java.util.HashSet;
import java.util.Set;

import com.apiflow.model.TimelineEntry;

public final class ScriptScope {

	private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
	private static final ThreadLocal<java.util.List<String>> LOG_SINK = new ThreadLocal<>();
	private static final ThreadLocal<java.util.List<TimelineEntry>> TIMELINE_SINK = new ThreadLocal<>();

	private ScriptScope() {
	}

	public static State begin() {
		return begin("developer");
	}

	public static State begin(String scriptMode) {
		State state = new State();
		state.scriptMode = scriptMode == null || scriptMode.isBlank() ? "safe" : scriptMode.trim();
		CURRENT.set(state);
		return state;
	}

	public static State current() {
		return CURRENT.get();
	}

	public static void clear() {
		CURRENT.remove();
	}

	public static void attachLogSink(java.util.List<String> sink) {
		LOG_SINK.set(sink);
	}

	public static void detachLogSink() {
		LOG_SINK.remove();
	}

	public static void attachTimelineSink(java.util.List<TimelineEntry> sink) {
		TIMELINE_SINK.set(sink);
	}

	public static void detachTimelineSink() {
		TIMELINE_SINK.remove();
	}

	public static void timeline(String kind, String label, String detail) {
		java.util.List<TimelineEntry> sink = TIMELINE_SINK.get();
		if (sink != null) {
			sink.add(new TimelineEntry(kind, label, System.currentTimeMillis(), detail == null ? "" : detail));
		}
	}

	public static final class State {

		private String scriptMode = "safe";
		private final Set<String> envKeys = new HashSet<>();
		private final Set<String> collectionKeys = new HashSet<>();
		private final java.util.List<String> logs = new java.util.ArrayList<>();

		public String getScriptMode() {
			return scriptMode;
		}

		public void markEnv(String key) {
			if (key != null && !key.isBlank()) {
				envKeys.add(key.trim());
				collectionKeys.remove(key.trim());
			}
		}

		public void markCollection(String key) {
			if (key != null && !key.isBlank()) {
				collectionKeys.add(key.trim());
				envKeys.remove(key.trim());
			}
		}

		public boolean isEnv(String key) {
			return key != null && envKeys.contains(key.trim());
		}

		public boolean isCollection(String key) {
			return key != null && collectionKeys.contains(key.trim());
		}

		public Set<String> getEnvKeys() {
			return envKeys;
		}

		public Set<String> getCollectionKeys() {
			return collectionKeys;
		}

		public void log(String message) {
			if (message != null && !message.isBlank()) {
				logs.add(message);
				java.util.List<String> sink = LOG_SINK.get();
				if (sink != null) {
					sink.add(message);
				}
			}
		}

		public java.util.List<String> getLogs() {
			return logs;
		}

	}

}
