package com.apiflow.service;

import java.util.List;

import com.apiflow.model.ExecuteResult;

final class FaultInjector {

	private static volatile List<String> prefixes = List.of();

	private FaultInjector() {
	}

	static void arm(List<String> values) {
		prefixes = values == null ? List.of() : List.copyOf(values);
	}

	static void clear() {
		prefixes = List.of();
	}

	static ExecuteResult check(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		for (String prefix : prefixes) {
			if (prefix != null && !prefix.isBlank() && url.contains(prefix)) {
				ExecuteResult result = ExecuteResult.failure("simulated dependency failure");
				result.setStatus(503);
				result.setStatusText("Simulated failure");
				return result;
			}
		}
		return null;
	}

}
