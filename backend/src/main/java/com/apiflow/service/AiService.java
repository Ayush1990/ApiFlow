package com.apiflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.apiflow.model.WorkspaceSettings;

@Service
public class AiService {

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final AiConversationStore conversations;

	public AiService(AiConversationStore conversations) {
		this.conversations = conversations;
	}

	public String suggestScript(String apiKey, String prompt, String context) {
		return chat(new WorkspaceSettings(), prompt, context, "script");
	}

	public String suggestAppHtml(WorkspaceSettings settings, String prompt, String context) {
		if (prompt == null || prompt.isBlank()) {
			throw new IllegalArgumentException("Prompt is required");
		}
		String system = """
			You write Bruno/ApiFlow HTML apps that run inside a sandbox iframe.
			Use bru.ctx.runRequest('Request Name') and bru.ctx.submitRequest({method,url,headers,body}).
			Return only a complete HTML document with inline CSS. No markdown fences.""";
		String user = prompt;
		if (context != null && !context.isBlank()) {
			user += "\n\nContext:\n" + context;
		}
		String provider = settings == null || settings.getAiProvider() == null ? "openai" : settings.getAiProvider().trim().toLowerCase();
		return switch (provider) {
			case "anthropic" -> anthropic(settings, system, user);
			case "custom" -> openAiCompatible(settings.getAiCustomUrl(), settings.getAiApiKey(), settings.getAiModel(), system, user);
			default -> openAi(settings.getAiApiKey(), settings.getAiModel(), system, user);
		};
	}

	public String chat(WorkspaceSettings settings, String prompt, String context, String mode) {
		return chat(settings, prompt, context, mode, null, null);
	}

	public String chat(WorkspaceSettings settings, String prompt, String context, String mode, String sessionId, List<AiConversationStore.AiMessage> history) {
		if (prompt == null || prompt.isBlank()) {
			throw new IllegalArgumentException("Prompt is required");
		}
		String provider = settings == null || settings.getAiProvider() == null ? "openai" : settings.getAiProvider().trim().toLowerCase();
		String system = "script".equals(mode)
			? "You write ApiFlow/Bruno JavaScript test and pre-request scripts. Return only JavaScript code."
			: "You are ApiFlow AI, a helpful assistant for API testing with Bruno-compatible bru/req/res scripts. Be concise.";
		StringBuilder user = new StringBuilder();
		if (history != null && !history.isEmpty()) {
			for (AiConversationStore.AiMessage message : history) {
				user.append(message.role()).append(": ").append(message.content()).append('\n');
			}
		}
		user.append("user: ").append(prompt);
		if (context != null && !context.isBlank()) {
			user.append("\n\nContext:\n").append(context);
		}
		String reply = switch (provider) {
			case "anthropic" -> anthropic(settings, system, user.toString());
			case "custom" -> openAiCompatible(settings.getAiCustomUrl(), settings.getAiApiKey(), settings.getAiModel(), system, user.toString());
			default -> openAi(settings.getAiApiKey(), settings.getAiModel(), system, user.toString());
		};
		if (sessionId != null && !sessionId.isBlank()) {
			conversations.append(sessionId, "user", prompt);
			conversations.append(sessionId, "assistant", reply);
		}
		return reply;
	}

	public void chatStream(WorkspaceSettings settings, String prompt, String context, String mode, String sessionId, Consumer<String> chunkConsumer) {
		String reply = chat(settings, prompt, context, mode, sessionId, conversations.history(sessionId));
		for (String token : reply.split("(?<=\\s)")) {
			chunkConsumer.accept(token);
		}
	}

	public List<AiConversationStore.AiMessage> history(String sessionId) {
		return conversations.history(sessionId);
	}

	public String ensureSession(String sessionId) {
		return conversations.ensureSession(sessionId);
	}

	private String openAi(String apiKey, String model, String system, String user) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("Add an OpenAI API key in Workspace settings");
		}
		return openAiCompatible("https://api.openai.com/v1/chat/completions", apiKey, model == null || model.isBlank() ? "gpt-4o-mini" : model, system, user);
	}

	private String openAiCompatible(String url, String apiKey, String model, String system, String user) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Custom AI URL is required");
		}
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("AI API key is required");
		}
		String body = """
				{
				  "model": %s,
				  "messages": [
				    {"role":"system","content":%s},
				    {"role":"user","content":%s}
				  ],
				  "temperature": 0.2
				}
				""".formatted(json(model == null || model.isBlank() ? "gpt-4o-mini" : model), json(system), json(user));
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
				.timeout(Duration.ofSeconds(90))
				.header("Authorization", "Bearer " + apiKey.trim())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("AI request failed: " + response.statusCode());
			}
			return extractOpenAiMessage(response.body());
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("AI helper failed: " + ex.getMessage());
		}
	}

	private String anthropic(WorkspaceSettings settings, String system, String user) {
		String apiKey = settings.getAiAnthropicKey();
		if (apiKey == null || apiKey.isBlank()) {
			apiKey = settings.getAiApiKey();
		}
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("Add an Anthropic API key in Workspace settings");
		}
		String model = settings.getAiModel();
		if (model == null || model.isBlank()) {
			model = "claude-3-5-sonnet-latest";
		}
		String body = """
				{
				  "model": %s,
				  "max_tokens": 4096,
				  "system": %s,
				  "messages": [
				    {"role":"user","content":%s}
				  ]
				}
				""".formatted(json(model), json(system), json(user));
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
				.timeout(Duration.ofSeconds(90))
				.header("x-api-key", apiKey.trim())
				.header("anthropic-version", "2023-06-01")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("Anthropic request failed: " + response.statusCode());
			}
			return extractAnthropicMessage(response.body());
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Anthropic helper failed: " + ex.getMessage());
		}
	}

	private static String extractOpenAiMessage(String json) {
		int contentIndex = json.indexOf("\"content\"");
		if (contentIndex < 0) {
			return json;
		}
		int start = json.indexOf('"', contentIndex + 9);
		if (start < 0) {
			return json;
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
				return out.toString().replace("\\n", "\n");
			}
			out.append(ch);
		}
		return out.toString();
	}

	private static String extractAnthropicMessage(String json) {
		int textIndex = json.indexOf("\"text\"");
		if (textIndex < 0) {
			return json;
		}
		int start = json.indexOf('"', textIndex + 6);
		if (start < 0) {
			return json;
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
				return out.toString().replace("\\n", "\n");
			}
			out.append(ch);
		}
		return out.toString();
	}

	private static String json(String value) {
		return "\"" + (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
	}

	public String suggestFlowBlocks(String prompt) {
		String system = """
			You design ApiFlow visual flows as JSON with blocks and connections.
			Return only JSON: {"name":"...","blocks":[{"id":"start","type":"start","label":"Start","x":40,"y":40,"config":"{}"},...],
			"connections":[{"from":"start","to":"req1"}]}.
			Block types: start, request, delay, transform, condition.""";
		try {
			return chat(new WorkspaceSettings(), prompt == null ? "Create a simple API workflow" : prompt, "", "flow");
		}
		catch (Exception ex) {
			return "{\"name\":\"Flow\",\"blocks\":[{\"id\":\"start\",\"type\":\"start\",\"label\":\"Start\",\"x\":40,\"y\":40,\"config\":\"{}\"}],\"connections\":[]}";
		}
	}

}
