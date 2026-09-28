package com.apiflow.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.apiflow.model.CheckResult;
import com.apiflow.model.KeyValue;

public final class SecretMasker {

	private SecretMasker() {
	}

	public static String mask(String text, List<String> secrets) {
		if (text == null || text.isBlank() || secrets == null || secrets.isEmpty()) {
			return text == null ? "" : text;
		}
		String current = text;
		for (String secret : secrets) {
			if (secret != null && secret.length() >= 4) {
				current = current.replace(secret, "••••");
			}
		}
		return current;
	}

	@SafeVarargs
	public static List<String> collectSecrets(List<KeyValue>... groups) {
		List<String> secrets = new ArrayList<>();
		if (groups == null) {
			return secrets;
		}
		for (List<KeyValue> group : groups) {
			collectFromGroup(secrets, group);
		}
		secrets.sort(Comparator.comparingInt(String::length).reversed());
		return secrets;
	}

	private static void collectFromGroup(List<String> secrets, List<KeyValue> group) {
		if (group == null) {
			return;
		}
		for (KeyValue variable : group) {
			if (variable.isSecret() && variable.getValue() != null && variable.getValue().length() >= 4) {
				secrets.add(variable.getValue());
			}
		}
	}

	public static List<CheckResult> maskChecks(List<CheckResult> checks, List<String> secrets) {
		if (checks == null || checks.isEmpty() || secrets == null || secrets.isEmpty()) {
			return checks;
		}
		List<CheckResult> masked = new ArrayList<>();
		for (CheckResult check : checks) {
			CheckResult copy = new CheckResult(check.getType(), check.isPassed(), mask(check.getMessage(), secrets));
			masked.add(copy);
		}
		return masked;
	}

}
