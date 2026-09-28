package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DigestAuth {

	private static final Pattern CHALLENGE = Pattern.compile("(\\w+)=\"([^\"]*)\"");

	private DigestAuth() {
	}

	public static String authorization(String username, String password, String method, String uri, String challenge) {
		String realm = value(challenge, "realm");
		String nonce = value(challenge, "nonce");
		String qop = value(challenge, "qop");
		String opaque = value(challenge, "opaque");
		String ha1 = md5(username + ":" + realm + ":" + password);
		String ha2 = md5(method.toUpperCase(Locale.ROOT) + ":" + uri);
		String nc = "00000001";
		String cnonce = md5(String.valueOf(System.nanoTime())).substring(0, 8);
		String response = qop.isBlank()
			? md5(ha1 + ":" + nonce + ":" + ha2)
			: md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2);
		StringBuilder header = new StringBuilder("Digest username=\"").append(username).append("\", realm=\"").append(realm)
			.append("\", nonce=\"").append(nonce).append("\", uri=\"").append(uri).append("\", response=\"").append(response).append("\"");
		if (!qop.isBlank()) {
			header.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
		}
		if (!opaque.isBlank()) {
			header.append(", opaque=\"").append(opaque).append("\"");
		}
		return header.toString();
	}

	public static String parseChallenge(String wwwAuthenticate) {
		return wwwAuthenticate == null ? "" : wwwAuthenticate.replace("Digest ", "").trim();
	}

	private static String value(String challenge, String key) {
		Matcher matcher = CHALLENGE.matcher(challenge);
		while (matcher.find()) {
			if (key.equalsIgnoreCase(matcher.group(1))) {
				return matcher.group(2);
			}
		}
		return "";
	}

	private static String md5(String input) {
		try {
			byte[] hash = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Digest hash failed");
		}
	}

}
