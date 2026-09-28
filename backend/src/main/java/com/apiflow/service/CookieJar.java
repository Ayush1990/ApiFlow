package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.apiflow.model.KeyValue;
import com.apiflow.model.StoredCookie;

public final class CookieJar {

	private CookieJar() {
	}

	public static List<StoredCookie> matching(List<StoredCookie> cookies, String host) {
		List<StoredCookie> matched = new ArrayList<>();
		if (cookies == null || host == null) {
			return matched;
		}
		String normalized = host.toLowerCase(Locale.ROOT);
		for (StoredCookie cookie : cookies) {
			if (cookie.getName() == null || cookie.getName().isBlank()) {
				continue;
			}
			String domain = cookie.getDomain() == null ? "" : cookie.getDomain().toLowerCase(Locale.ROOT);
			if (domain.startsWith(".")) {
				domain = domain.substring(1);
			}
			if (normalized.equals(domain) || normalized.endsWith("." + domain)) {
				matched.add(cookie);
			}
		}
		return matched;
	}

	public static StoredCookie parse(String setCookie, String host) {
		if (setCookie == null || setCookie.isBlank()) {
			return null;
		}
		String[] parts = setCookie.split(";");
		int eq = parts[0].indexOf('=');
		if (eq <= 0) {
			return null;
		}
		StoredCookie cookie = new StoredCookie();
		cookie.setName(parts[0].substring(0, eq).trim());
		cookie.setValue(parts[0].substring(eq + 1).trim());
		cookie.setDomain(host);
		cookie.setPath("/");
		for (int i = 1; i < parts.length; i++) {
			String[] attr = parts[i].split("=", 2);
			String key = attr[0].trim();
			String value = attr.length > 1 ? attr[1].trim() : "";
			if (key.equalsIgnoreCase("domain") && !value.isEmpty()) {
				cookie.setDomain(value.startsWith(".") ? value.substring(1) : value);
			}
			else if (key.equalsIgnoreCase("path") && !value.isEmpty()) {
				cookie.setPath(value);
			}
		}
		return cookie;
	}

	public static void remember(List<StoredCookie> jar, StoredCookie incoming) {
		if (incoming == null) {
			return;
		}
		jar.removeIf(cookie -> incoming.getName().equals(cookie.getName())
				&& incoming.getDomain().equalsIgnoreCase(cookie.getDomain()));
		jar.add(incoming);
	}

	public static String header(List<StoredCookie> cookies) {
		StringBuilder value = new StringBuilder();
		for (StoredCookie cookie : cookies) {
			if (!value.isEmpty()) {
				value.append("; ");
			}
			value.append(cookie.getName()).append('=').append(cookie.getValue() == null ? "" : cookie.getValue());
		}
		return value.toString();
	}

	public static List<KeyValue> asPairs(List<StoredCookie> cookies) {
		List<KeyValue> pairs = new ArrayList<>();
		for (StoredCookie cookie : cookies) {
			pairs.add(new KeyValue(cookie.getName(), cookie.getValue(), true));
		}
		return pairs;
	}

}
