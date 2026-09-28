package com.apiflow.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class EdgeGridSigner {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ssZ").withZone(ZoneOffset.UTC);

	private EdgeGridSigner() {
	}

	public static Map<String, String> sign(String clientToken, String clientSecret, String accessToken, String method, URI uri, String body, Map<String, String> headers) {
		String timestamp = TIMESTAMP.format(Instant.now());
		String authHeader = "EG1-HMAC-SHA256 client_token=" + clientToken + ";access_token=" + accessToken + ";timestamp=" + timestamp + ";nonce=" + java.util.UUID.randomUUID() + ";";
		String dataToSign = buildDataToSign(method, uri, headers, authHeader, body);
		String signingKey = hmacSha256Base64(timestamp, clientSecret);
		String signature = hmacSha256Base64(dataToSign, signingKey);
		Map<String, String> signed = new LinkedHashMap<>();
		signed.put("Authorization", authHeader + "signature=" + signature);
		return signed;
	}

	private static String buildDataToSign(String method, URI uri, Map<String, String> headers, String authHeader, String body) {
		String canonicalHeaders = headers.entrySet().stream()
			.filter(entry -> entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).startsWith("x-akamai-"))
			.sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
			.map(entry -> entry.getKey().trim().toLowerCase(Locale.ROOT) + ":" + (entry.getValue() == null ? "" : entry.getValue().trim()))
			.collect(Collectors.joining("\t"));
		String bodyHash = base64Sha256(body == null ? "" : body);
		return method.toUpperCase(Locale.ROOT) + "\t"
			+ uri.getScheme().toLowerCase(Locale.ROOT) + "\t"
			+ uri.getHost().toLowerCase(Locale.ROOT) + "\t"
			+ (uri.getRawPath() == null ? "/" : uri.getRawPath()) + "\t"
			+ canonicalHeaders + "\t"
			+ bodyHash + "\t"
			+ authHeader;
	}

	private static String base64Sha256(String value) {
		try {
			byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("EdgeGrid hash failed");
		}
	}

	private static String hmacSha256Base64(String data, String key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("EdgeGrid signing failed");
		}
	}

}
