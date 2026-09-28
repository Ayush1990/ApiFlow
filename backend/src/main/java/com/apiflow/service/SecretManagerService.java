package com.apiflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.apiflow.model.SecretManagerSettings;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Service
public class SecretManagerService {

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Map<String, String> cache = new ConcurrentHashMap<>();

	public String resolve(String reference, SecretManagerSettings settings) {
		if (reference == null || reference.isBlank() || settings == null) {
			return "";
		}
		String key = settings.getProvider() + ":" + reference.trim();
		return cache.computeIfAbsent(key, ignored -> fetch(reference.trim(), settings));
	}

	public void clearCache() {
		cache.clear();
	}

	private String fetch(String reference, SecretManagerSettings settings) {
		String provider = settings.getProvider();
		if ("aws".equalsIgnoreCase(provider)) {
			return aws(reference, settings);
		}
		if ("vault".equalsIgnoreCase(provider)) {
			return vault(reference, settings);
		}
		if ("azure".equalsIgnoreCase(provider)) {
			return azure(reference, settings);
		}
		if ("gcp".equalsIgnoreCase(provider)) {
			return gcp(reference, settings);
		}
		return "";
	}

	private String aws(String secretId, SecretManagerSettings settings) {
		try (SecretsManagerClient client = SecretsManagerClient.builder().region(Region.of(settings.getAwsRegion())).build()) {
			return client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build()).secretString();
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("AWS secret lookup failed: " + ex.getMessage());
		}
	}

	private String vault(String path, SecretManagerSettings settings) {
		if (settings.getVaultUrl().isBlank() || settings.getVaultToken().isBlank()) {
			throw new IllegalArgumentException("Vault URL and token are required");
		}
		String url = settings.getVaultUrl().replaceAll("/+$", "") + "/v1/" + path.replaceAll("^/+", "");
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("X-Vault-Token", settings.getVaultToken())
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("Vault returned " + response.statusCode());
			}
			String body = response.body();
			int dataIndex = body.indexOf("\"data\"");
			if (dataIndex < 0) {
				return body;
			}
			int valueIndex = body.indexOf("\"value\"", dataIndex);
			if (valueIndex >= 0) {
				return extractJsonString(body, valueIndex + 7);
			}
			return body;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Vault lookup failed: " + ex.getMessage());
		}
	}

	private String azure(String secretName, SecretManagerSettings settings) {
		if (settings.getAzureVaultUrl().isBlank()) {
			throw new IllegalArgumentException("Azure Key Vault URL is required");
		}
		try {
			String token = azureToken(settings);
			String url = settings.getAzureVaultUrl().replaceAll("/+$", "") + "/secrets/" + secretName + "?api-version=7.4";
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bearer " + token)
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("Azure returned " + response.statusCode());
			}
			return extractJsonString(response.body(), response.body().indexOf("\"value\"") + 7);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Azure secret lookup failed: " + ex.getMessage());
		}
	}

	private String azureToken(SecretManagerSettings settings) throws Exception {
		if ("cli".equalsIgnoreCase(settings.getAzureAuthMode())) {
			return azureCliToken();
		}
		if (settings.getAzureTenantId().isBlank() || settings.getAzureClientId().isBlank() || settings.getAzureClientSecret().isBlank()) {
			throw new IllegalArgumentException("Azure tenant, client ID, and secret are required");
		}
		String form = "client_id=" + encode(settings.getAzureClientId())
			+ "&scope=" + encode("https://vault.azure.net/.default")
			+ "&client_secret=" + encode(settings.getAzureClientSecret())
			+ "&grant_type=client_credentials";
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/" + settings.getAzureTenantId() + "/oauth2/v2.0/token"))
			.timeout(Duration.ofSeconds(15))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form))
			.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() >= 400) {
			throw new IllegalArgumentException("Azure token request failed");
		}
		return extractJsonString(response.body(), response.body().indexOf("\"access_token\"") + 15);
	}

	private String gcp(String secretName, SecretManagerSettings settings) {
		if (settings.getGcpProjectId().isBlank() || settings.getGcpAccessToken().isBlank()) {
			throw new IllegalArgumentException("GCP project ID and access token are required");
		}
		String version = secretName.contains("/") ? secretName : secretName + "/versions/latest";
		String url = "https://secretmanager.googleapis.com/v1/projects/" + settings.getGcpProjectId() + "/secrets/" + version + ":access";
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bearer " + settings.getGcpAccessToken())
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("GCP returned " + response.statusCode());
			}
			String payload = extractJsonString(response.body(), response.body().indexOf("\"payload\""));
			int dataIndex = payload.indexOf("\"data\"");
			if (dataIndex >= 0) {
				return new String(java.util.Base64.getDecoder().decode(extractJsonString(payload, dataIndex + 6)), StandardCharsets.UTF_8);
			}
			return payload;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("GCP secret lookup failed: " + ex.getMessage());
		}
	}

	private static String extractJsonString(String json, int fromIndex) {
		if (json == null || fromIndex < 0) {
			return "";
		}
		int start = json.indexOf('"', fromIndex);
		if (start < 0) {
			return "";
		}
		start += 1;
		StringBuilder out = new StringBuilder();
		boolean escaped = false;
		for (int index = start; index < json.length(); index++) {
			char ch = json.charAt(index);
			if (escaped) {
				out.append(ch);
				escaped = false;
				continue;
			}
			if (ch == '\\') {
				escaped = true;
				continue;
			}
			if (ch == '"') {
				return out.toString();
			}
			out.append(ch);
		}
		return out.toString();
	}

	private static String encode(String value) {
		return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private String azureCliToken() throws Exception {
		ProcessBuilder builder = new ProcessBuilder("az", "account", "get-access-token", "--resource", "https://vault.azure.net", "--query", "accessToken", "-o", "tsv");
		builder.redirectErrorStream(true);
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
		int code = process.waitFor();
		if (code != 0 || output.isBlank()) {
			throw new IllegalArgumentException("Azure CLI auth failed. Run `az login` and ensure Azure CLI is installed.");
		}
		return output.lines().findFirst().orElse("").trim();
	}

}
