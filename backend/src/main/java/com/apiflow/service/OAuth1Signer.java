package com.apiflow.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class OAuth1Signer {

	private OAuth1Signer() {
	}

	public static String authorization(String consumerKey, String consumerSecret, String token, String tokenSecret,
			String method, URI uri, Map<String, String> extraParams) {
		String nonce = UUID.randomUUID().toString().replace("-", "");
		String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
		Map<String, String> params = new TreeMap<>();
		params.put("oauth_consumer_key", consumerKey);
		params.put("oauth_nonce", nonce);
		params.put("oauth_signature_method", "HMAC-SHA1");
		params.put("oauth_timestamp", timestamp);
		params.put("oauth_version", "1.0");
		if (token != null && !token.isBlank()) {
			params.put("oauth_token", token);
		}
		if (extraParams != null) {
			params.putAll(extraParams);
		}
		if (uri.getRawQuery() != null) {
			for (String part : uri.getRawQuery().split("&")) {
				if (part.isBlank()) {
					continue;
				}
				int equals = part.indexOf('=');
				String key = decode(equals < 0 ? part : part.substring(0, equals));
				String value = decode(equals < 0 ? "" : part.substring(equals + 1));
				params.put(key, value);
			}
		}
		String base = method.toUpperCase(Locale.ROOT) + "&" + encode(normalize(uri)) + "&" + encode(normalizeParams(params));
		String signingKey = encode(consumerSecret) + "&" + encode(tokenSecret == null ? "" : tokenSecret);
		String signature = hmacSha1(base, signingKey);
		params.put("oauth_signature", signature);
		StringBuilder header = new StringBuilder("OAuth ");
		boolean first = true;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (!entry.getKey().startsWith("oauth_")) {
				continue;
			}
			if (!first) {
				header.append(", ");
			}
			first = false;
			header.append(encode(entry.getKey())).append("=\"").append(encode(entry.getValue())).append('"');
		}
		return header.toString();
	}

	private static String normalize(URI uri) {
		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		int port = uri.getPort();
		if (port < 0) {
			port = "https".equals(scheme) ? 443 : 80;
		}
		String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
		if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
			return scheme + "://" + host + path;
		}
		return scheme + "://" + host + ":" + port + path;
	}

	private static String normalizeParams(Map<String, String> params) {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (builder.length() > 0) {
				builder.append('&');
			}
			builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
		}
		return builder.toString();
	}

	private static String hmacSha1(String base, String key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
			return Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("OAuth1 signing failed");
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
			.replace("+", "%20")
			.replace("*", "%2A")
			.replace("%7E", "~");
	}

	private static String decode(String value) {
		return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

}
