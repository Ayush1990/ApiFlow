package com.apiflow.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RunnerContext {

	private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

	private RunnerContext() {
	}

	public static State begin(int iterationIndex, int totalIterations, Map<String, String> row) {
		State state = new State(iterationIndex, totalIterations, row == null ? Map.of() : new LinkedHashMap<>(row));
		CURRENT.set(state);
		return state;
	}

	public static State current() {
		return CURRENT.get();
	}

	public static void clear() {
		CURRENT.remove();
	}

	public static final class State {

		private String nextRequest;
		private boolean skipCurrent;
		private boolean stopRun;
		private final int iterationIndex;
		private final int totalIterations;
		private final Map<String, String> iterationRow;

		State(int iterationIndex, int totalIterations, Map<String, String> iterationRow) {
			this.iterationIndex = iterationIndex;
			this.totalIterations = totalIterations;
			this.iterationRow = iterationRow;
		}

		public String getNextRequest() {
			return nextRequest == null ? "" : nextRequest;
		}

		public void setNextRequest(String nextRequest) {
			this.nextRequest = nextRequest;
		}

		public boolean isSkipCurrent() {
			return skipCurrent;
		}

		public void setSkipCurrent(boolean skipCurrent) {
			this.skipCurrent = skipCurrent;
		}

		public boolean isStopRun() {
			return stopRun;
		}

		public void setStopRun(boolean stopRun) {
			this.stopRun = stopRun;
		}

		public int getIterationIndex() {
			return iterationIndex;
		}

		public int getTotalIterations() {
			return totalIterations;
		}

		public Map<String, String> getIterationRow() {
			return iterationRow;
		}

		public boolean hasIterationKey(String key) {
			return iterationRow.containsKey(key);
		}

		public String getIterationValue(String key) {
			return iterationRow.getOrDefault(key, "");
		}

		public void unsetIterationKey(String key) {
			iterationRow.remove(key);
		}

	}

}
