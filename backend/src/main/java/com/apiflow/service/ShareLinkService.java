package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.ExecuteResult;
import com.apiflow.store.FileStore;

import tools.jackson.databind.ObjectMapper;

@Service
public class ShareLinkService {

	private final FileStore store;
	private final ObjectMapper mapper = new ObjectMapper();

	public ShareLinkService(FileStore store) {
		this.store = store;
	}

	public ShareLink create(ApiRequest request, ExecuteResult response) {
		return create(request, response, "");
	}

	public ShareLink create(ApiRequest request, ExecuteResult response, String baseUrl) {
		String id = UUID.randomUUID().toString();
		SharePayload payload = new SharePayload(
			request.getName(),
			request.getMethod(),
			request.getUrl(),
			request.getHeaders(),
			request.getBody(),
			response.getStatus(),
			response.getStatusText(),
			response.getBody(),
			response.getContentType(),
			System.currentTimeMillis());
		Path dir = store.root().resolve("shares");
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(id + ".json"), mapper.writeValueAsString(payload), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not create share link");
		}
		String publicPath = "/share/" + id;
		String origin = normalizeBaseUrl(baseUrl);
		return new ShareLink(id, "/api/platform/share/" + id, publicPath, origin + publicPath);
	}

	private static String normalizeBaseUrl(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return "http://localhost:8080";
		}
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public SharePayload read(String id) {
		Path file = store.root().resolve("shares").resolve(id + ".json");
		if (!Files.exists(file)) {
			throw new IllegalArgumentException("Share link not found or expired");
		}
		try {
			return mapper.readValue(Files.readString(file, StandardCharsets.UTF_8), SharePayload.class);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not read share link");
		}
	}

	public void revoke(String id) {
		try {
			Files.deleteIfExists(store.root().resolve("shares").resolve(id + ".json"));
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not revoke share link");
		}
	}

	public record ShareLink(String id, String apiUrl, String publicPath, String publicUrl) {
	}

	public record SharePayload(
		String requestName,
		String method,
		String url,
		Object headers,
		String body,
		int status,
		String statusText,
		String responseBody,
		String contentType,
		long createdAt) {
	}

}
