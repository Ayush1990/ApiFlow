package com.apiflow.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.RequestExtras;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OAuthService {

	private final ObjectMapper mapper = new ObjectMapper();
	private final Map<String, Pending> pending = new ConcurrentHashMap<>();
	private final SecureRandom random = new SecureRandom();

	public void apply(ExecuteCommand command, Map<String, String> variables) {
		if (command == null || !"oauth2".equalsIgnoreCase(command.getAuthType())) {
			return;
		}
		RequestExtras extras = command.getExtras();
		String grant = extras.getOauthGrant();
		String token = extras.getOauthAccessToken();
		boolean expired = extras.getOauthExpiresAt() > 0 && extras.getOauthExpiresAt() <= System.currentTimeMillis();
		if (!token.isBlank() && !expired) {
			command.setAuthType("bearer");
			command.setAuthToken(token);
			return;
		}
		if (!extras.getOauthRefreshToken().isBlank()) {
			TokenSet refreshed = refresh(
				RequestExecutor.interpolate(extras.getOauthTokenUrl(), variables),
				RequestExecutor.interpolate(extras.getOauthClientId(), variables),
				RequestExecutor.interpolate(extras.getOauthClientSecret(), variables),
				extras.getOauthRefreshToken());
			store(extras, refreshed);
			command.setAuthType("bearer");
			command.setAuthToken(extras.getOauthAccessToken());
			return;
		}
		if ("authorization_code".equals(grant)) {
			throw new IllegalArgumentException("OAuth token expired. Sign in again.");
		}
		if ("device_code".equals(grant)) {
			throw new IllegalArgumentException("Complete device code sign-in before sending this request.");
		}
		if ("password".equals(grant)) {
			TokenSet issued = passwordGrant(
				RequestExecutor.interpolate(extras.getOauthTokenUrl(), variables),
				RequestExecutor.interpolate(extras.getOauthClientId(), variables),
				RequestExecutor.interpolate(extras.getOauthClientSecret(), variables),
				RequestExecutor.interpolate(extras.getOauthUsername(), variables),
				RequestExecutor.interpolate(extras.getOauthPassword(), variables),
				RequestExecutor.interpolate(extras.getOauthScope(), variables));
			store(extras, issued);
			command.setAuthType("bearer");
			command.setAuthToken(extras.getOauthAccessToken());
			return;
		}
		TokenSet issued = clientCredentials(
			RequestExecutor.interpolate(extras.getOauthTokenUrl(), variables),
			RequestExecutor.interpolate(extras.getOauthClientId(), variables),
			RequestExecutor.interpolate(extras.getOauthClientSecret(), variables),
			RequestExecutor.interpolate(extras.getOauthScope(), variables));
		store(extras, issued);
		command.setAuthType("bearer");
		command.setAuthToken(extras.getOauthAccessToken());
	}

	public DeviceStarted startDevice(String deviceUrl, String clientId, String scope) {
		if (deviceUrl == null || deviceUrl.isBlank()) {
			throw new IllegalArgumentException("Device authorization URL is required");
		}
		String body = "client_id=" + encode(clientId) + "&scope=" + encode(scope == null ? "" : scope);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(deviceUrl))
				.timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
			JsonNode node = mapper.readTree(response.body());
			return new DeviceStarted(
				node.path("device_code").asString(""),
				node.path("user_code").asString(""),
				node.path("verification_uri_complete").asString(node.path("verification_uri").asString("")));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Device authorization failed: " + ex.getMessage());
		}
	}

	public TokenResponse pollDevice(String tokenUrl, String clientId, String clientSecret, String deviceCode) {
		String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=" + encode(deviceCode)
			+ "&client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret);
		TokenSet token = readToken(tokenUrl, body);
		return new TokenResponse(token.access(), token.refresh(), token.expiresAt());
	}

	public Started start(String authUrl, String tokenUrl, String clientId, String clientSecret, String scope) {
		if (authUrl == null || authUrl.isBlank() || tokenUrl == null || tokenUrl.isBlank()) {
			throw new IllegalArgumentException("Auth URL and token URL are required");
		}
		String state = UUID.randomUUID().toString();
		String verifier = verifier();
		String redirect = "http://localhost:8080/api/oauth/callback";
		pending.put(state, new Pending(tokenUrl, clientId == null ? "" : clientId, clientSecret == null ? "" : clientSecret, redirect, verifier, Instant.now().plusSeconds(600)));
		String url = authUrl + (authUrl.contains("?") ? "&" : "?")
			+ "response_type=code&client_id=" + encode(clientId)
			+ "&redirect_uri=" + encode(redirect)
			+ "&state=" + encode(state)
			+ "&scope=" + encode(scope == null ? "" : scope)
			+ "&code_challenge=" + encode(challenge(verifier))
			+ "&code_challenge_method=S256";
		return new Started(url, state);
	}

	public String callback(String code, String state) {
		Pending item = pending.remove(state);
		if (item == null || item.expires.isBefore(Instant.now())) {
			return page(null, "That sign-in expired. Close this window and try again.");
		}
		if (code == null || code.isBlank()) {
			return page(null, "The provider did not return a code.");
		}
		try {
			TokenSet token = exchange(item, code);
			return page(token, "Signed in. You can close this window.");
		}
		catch (Exception ex) {
			return page(null, ex.getMessage() == null ? "Token request failed" : ex.getMessage());
		}
	}

	private TokenSet passwordGrant(String tokenUrl, String clientId, String clientSecret, String username, String password, String scope) {
		if (tokenUrl == null || tokenUrl.isBlank()) {
			throw new IllegalArgumentException("OAuth token URL is required");
		}
		String body = "grant_type=password&username=" + encode(username) + "&password=" + encode(password)
			+ "&client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret) + "&scope=" + encode(scope);
		return readToken(tokenUrl, body);
	}

	private TokenSet clientCredentials(String tokenUrl, String clientId, String clientSecret, String scope) {
		if (tokenUrl == null || tokenUrl.isBlank()) {
			throw new IllegalArgumentException("OAuth token URL is required");
		}
		String body = "grant_type=client_credentials&client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret) + "&scope=" + encode(scope);
		return readToken(tokenUrl, body);
	}

	private TokenSet refresh(String tokenUrl, String clientId, String clientSecret, String refreshToken) {
		if (tokenUrl == null || tokenUrl.isBlank()) {
			throw new IllegalArgumentException("OAuth token URL is required");
		}
		String body = "grant_type=refresh_token&refresh_token=" + encode(refreshToken) + "&client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret);
		return readToken(tokenUrl, body);
	}

	private TokenSet exchange(Pending item, String code) {
		String body = "grant_type=authorization_code&code=" + encode(code)
			+ "&redirect_uri=" + encode(item.redirect)
			+ "&client_id=" + encode(item.clientId)
			+ "&client_secret=" + encode(item.clientSecret)
			+ "&code_verifier=" + encode(item.verifier);
		return readToken(item.tokenUrl, body);
	}

	private void store(RequestExtras extras, TokenSet token) {
		String previous = extras.getOauthRefreshToken();
		extras.setOauthAccessToken(token.access);
		extras.setOauthRefreshToken(token.refresh.isBlank() ? previous : token.refresh);
		extras.setOauthExpiresAt(token.expiresAt);
	}

	TokenSet readToken(String tokenUrl, String body) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
				.timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("Token URL returned " + response.statusCode());
			}
			JsonNode node = mapper.readTree(response.body());
			String token = node.path("access_token").asString("");
			if (token == null || token.isBlank()) {
				throw new IllegalArgumentException("Token URL did not return an access_token");
			}
			long expiresIn = node.path("expires_in").asLong(0);
			long lifetime = expiresIn > 30 ? expiresIn - 30 : (expiresIn > 0 ? expiresIn : 3600);
			long expiresAt = Instant.now().plusSeconds(lifetime).toEpochMilli();
			String refresh = node.path("refresh_token").asString("");
			return new TokenSet(token, refresh == null ? "" : refresh, expiresAt);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("OAuth token request failed: " + ex.getMessage());
		}
	}

	private String verifier() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String challenge(String verifier) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not build the PKCE challenge");
		}
	}

	private static String page(TokenSet token, String message) {
		String script = token == null || token.access.isBlank() ? "" : """
				<script>
				if (window.opener) {
				  window.opener.postMessage({ type: 'apiflow-oauth', token: %s, refresh: %s, expiresAt: %d }, 'http://localhost:5173')
				}
				</script>
				""".formatted(json(token.access), json(token.refresh), token.expiresAt);
		return "<!doctype html><title>ApiFlow</title><p>" + escape(message) + "</p>" + script;
	}

	private static String json(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;");
	}

	private static String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	public record Started(String url, String state, boolean systemBrowser) {
		public Started(String url, String state) {
			this(url, state, true);
		}
	}

	public record DeviceStarted(String deviceCode, String userCode, String verificationUri) {
	}

	public record TokenResponse(String access, String refresh, long expiresAt) {
	}

	private record Pending(String tokenUrl, String clientId, String clientSecret, String redirect, String verifier, Instant expires) {
	}

	private record TokenSet(String access, String refresh, long expiresAt) {
	}

}
