package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class AiConversationStore {

	private final Map<String, List<AiMessage>> sessions = new ConcurrentHashMap<>();

	public String ensureSession(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			sessionId = UUID.randomUUID().toString();
		}
		sessions.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
		return sessionId;
	}

	public List<AiMessage> history(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return List.of();
		}
		return List.copyOf(sessions.getOrDefault(sessionId, List.of()));
	}

	public void append(String sessionId, String role, String content) {
		if (sessionId == null || sessionId.isBlank()) {
			return;
		}
		sessions.computeIfAbsent(sessionId, ignored -> new ArrayList<>())
			.add(new AiMessage(role, content == null ? "" : content, System.currentTimeMillis()));
		while (sessions.get(sessionId).size() > 40) {
			sessions.get(sessionId).remove(0);
		}
	}

	public void clear(String sessionId) {
		if (sessionId != null) {
			sessions.remove(sessionId);
		}
	}

	public record AiMessage(String role, String content, long at) {
	}

}
