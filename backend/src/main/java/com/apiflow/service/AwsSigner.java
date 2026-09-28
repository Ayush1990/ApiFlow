package com.apiflow.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AwsSigner {

	private AwsSigner() {
	}

	public static Map<String, String> sign(String accessKey, String secretKey, String region, String service, String method, URI uri, String payload, Map<String, String> headers) {
		String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
		String dateStamp = amzDate.substring(0, 8);
		TreeMap<String, String> signed = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		if (headers != null) {
			signed.putAll(headers);
		}
		signed.put("host", uri.getHost());
		signed.put("x-amz-date", amzDate);
		signed.put("x-amz-content-sha256", sha256(payload == null ? "" : payload));
		String canonicalHeaders = canonicalHeaders(signed);
		String signedHeaders = String.join(";", signed.keySet().stream().map(k -> k.toLowerCase(Locale.ROOT)).sorted().toList());
		String canonicalRequest = method.toUpperCase(Locale.ROOT) + "\n" + canonicalUri(uri) + "\n" + canonicalQuery(uri) + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + signed.get("x-amz-content-sha256");
		String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
		String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + credentialScope + "\n" + sha256(canonicalRequest);
		byte[] signingKey = signingKey(secretKey, dateStamp, region, service);
		String signature = hmacHex(signingKey, stringToSign);
		signed.put("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
		return signed;
	}

	private static String canonicalUri(URI uri) {
		String path = uri.getPath();
		return path == null || path.isBlank() ? "/" : path;
	}

	private static String canonicalQuery(URI uri) {
		if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
			return "";
		}
		TreeMap<String, String> params = new TreeMap<>();
		for (String part : uri.getRawQuery().split("&")) {
			String[] pair = part.split("=", 2);
			params.put(pair[0], pair.length > 1 ? pair[1] : "");
		}
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (!out.isEmpty()) {
				out.append('&');
			}
			out.append(entry.getKey()).append('=').append(entry.getValue());
		}
		return out.toString();
	}

	private static String canonicalHeaders(Map<String, String> headers) {
		StringBuilder out = new StringBuilder();
		TreeMap<String, String> sorted = new TreeMap<>();
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			sorted.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue() == null ? "" : entry.getValue().trim());
		}
		for (Map.Entry<String, String> entry : sorted.entrySet()) {
			out.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
		}
		return out.toString();
	}

	private static byte[] signingKey(String secret, String date, String region, String service) {
		byte[] kDate = hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), date);
		byte[] kRegion = hmac(kDate, region);
		byte[] kService = hmac(kRegion, service);
		return hmac(kService, "aws4_request");
	}

	private static String sha256(String value) {
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("SHA-256 failed");
		}
	}

	private static byte[] hmac(byte[] key, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("HMAC failed");
		}
	}

	private static String hmacHex(byte[] key, String data) {
		return HexFormat.of().formatHex(hmac(key, data));
	}

}
