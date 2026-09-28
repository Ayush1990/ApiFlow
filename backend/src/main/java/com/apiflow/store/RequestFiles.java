package com.apiflow.store;

import java.util.Locale;

import com.apiflow.model.ApiRequest;

public final class RequestFiles {

	private RequestFiles() {
	}

	public static String name(ApiRequest request) {
		String slug = slug(request.getName());
		String id = request.getId() == null ? "request" : request.getId();
		String suffix = id.length() > 8 ? id.substring(0, 8) : id;
		return slug + "-" + suffix + ".json";
	}

	static String slug(String name) {
		if (name == null || name.isBlank()) {
			return "request";
		}
		String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		if (slug.isBlank()) {
			return "request";
		}
		return slug.length() > 48 ? slug.substring(0, 48).replaceAll("-$", "") : slug;
	}

}
