package com.apiflow.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TagExpression {

	private static final Pattern TAG_CALL = Pattern.compile("@tag\\s*\\(\\s*([^)]+?)\\s*\\)", Pattern.CASE_INSENSITIVE);

	private TagExpression() {
	}

	public static boolean matches(List<String> requestTags, String expression) {
		if (expression == null || expression.isBlank()) {
			return true;
		}
		if (requestTags == null || requestTags.isEmpty()) {
			return false;
		}
		String normalized = normalize(expression);
		if (normalized.contains("||")) {
			for (String part : normalized.split("\\|\\|")) {
				if (matches(requestTags, part.trim())) {
					return true;
				}
			}
			return false;
		}
		if (normalized.contains("&&")) {
			for (String part : normalized.split("&&")) {
				if (!matchesSingle(requestTags, part.trim())) {
					return false;
				}
			}
			return true;
		}
		return matchesSingle(requestTags, normalized);
	}

	private static boolean matchesSingle(List<String> requestTags, String token) {
		if (token.isBlank()) {
			return true;
		}
		Matcher matcher = TAG_CALL.matcher(token);
		if (matcher.matches()) {
			return hasTag(requestTags, matcher.group(1).trim());
		}
		return hasTag(requestTags, token);
	}

	private static boolean hasTag(List<String> requestTags, String wanted) {
		for (String tag : requestTags) {
			if (tag != null && wanted.equalsIgnoreCase(tag.trim())) {
				return true;
			}
		}
		return false;
	}

	private static String normalize(String expression) {
		return expression.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

}
