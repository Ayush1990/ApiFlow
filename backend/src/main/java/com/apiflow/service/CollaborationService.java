package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.ChangeResult;
import com.apiflow.model.Comment;
import com.apiflow.model.InventoryApp;
import com.apiflow.model.InventoryApp.CapturedCall;
import com.apiflow.model.MonitorRun;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.RunReport;
import com.apiflow.model.Webhook;
import com.apiflow.model.Workspace;
import com.apiflow.model.WorkspaceMember;
import com.apiflow.store.FileStore;

@Service
public class CollaborationService {

	private final FileStore store;
	private final WorkspaceService workspaceService;
	private final MonitorService monitorService;

	public CollaborationService(FileStore store, WorkspaceService workspaceService, MonitorService monitorService) {
		this.store = store;
		this.workspaceService = workspaceService;
		this.monitorService = monitorService;
		VaultStore.bind(store);
	}

	public List<Comment> comments(String targetId) {
		return store.copy().getComments().stream()
			.filter(item -> targetId == null || targetId.isBlank() || targetId.equals(item.getTargetId()))
			.toList();
	}

	public ChangeResult addComment(Comment comment) {
		if (comment.getBody() == null || comment.getBody().isBlank()) {
			throw new IllegalArgumentException("Comment body is required");
		}
		requireRole("editor");
		if (comment.getId() == null || comment.getId().isBlank()) {
			comment.setId(UUID.randomUUID().toString());
		}
		comment.setCreatedAt(System.currentTimeMillis());
		if (comment.getAuthor() == null || comment.getAuthor().isBlank()) {
			comment.setAuthor("local");
		}
		String id = comment.getId();
		Workspace workspace = store.update(data -> data.getComments().add(0, comment));
		return new ChangeResult(workspace, id);
	}

	public List<WorkspaceMember> members() {
		return store.copy().getMembers();
	}

	public ChangeResult saveMember(WorkspaceMember member) {
		requireRole("owner");
		if (member.getId() == null || member.getId().isBlank()) {
			member.setId(UUID.randomUUID().toString());
		}
		String id = member.getId();
		Workspace workspace = store.update(data -> {
			if (member.isActive()) {
				for (WorkspaceMember existing : data.getMembers()) {
					existing.setActive(false);
				}
			}
			data.getMembers().removeIf(item -> item.getId().equals(id));
			data.getMembers().add(member);
		});
		return new ChangeResult(workspace, id);
	}

	public List<Webhook> webhooks() {
		return store.copy().getWebhooks();
	}

	public ChangeResult saveWebhook(Webhook webhook) {
		requireRole("editor");
		if (webhook.getId() == null || webhook.getId().isBlank()) {
			webhook.setId(UUID.randomUUID().toString());
		}
		String id = webhook.getId();
		Workspace workspace = store.update(data -> {
			data.getWebhooks().removeIf(item -> item.getId().equals(id));
			data.getWebhooks().add(webhook);
		});
		return new ChangeResult(workspace, id);
	}

	public Object triggerWebhook(String id) {
		Webhook webhook = store.copy().getWebhooks().stream().filter(item -> item.getId().equals(id)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Webhook not found"));
		if (!webhook.isEnabled()) {
			throw new IllegalArgumentException("Webhook is paused");
		}
		if ("monitor".equalsIgnoreCase(webhook.getTargetType())) {
			return monitorService.run(webhook.getTargetId());
		}
		return workspaceService.runCollection(webhook.getTargetId(), webhook.getEnvironmentId(), "", "", false, "", false, 0, List.of());
	}

	public InventoryApp capture(String name, String environment, List<CapturedCall> calls) {
		requireRole("editor");
		InventoryApp app = new InventoryApp();
		app.setId(UUID.randomUUID().toString());
		app.setName(name == null || name.isBlank() ? "Application" : name);
		app.setEnvironment(environment == null ? "" : environment);
		app.setCapturedAt(System.currentTimeMillis());
		List<CapturedCall> stored = new ArrayList<>();
		int matched = 0;
		for (CapturedCall call : calls == null ? List.<CapturedCall>of() : calls) {
			ApiRequest request = matchRequest(call);
			call.setMatched(request != null);
			call.setRequestName(request == null ? "" : request.getName());
			if (request != null) {
				matched++;
			}
			stored.add(call);
		}
		app.setCalls(stored);
		app.setMatched(matched);
		app.setUnmatched(stored.size() - matched);
		store.update(data -> {
			data.getInventory().add(0, app);
			while (data.getInventory().size() > 30) {
				data.getInventory().remove(data.getInventory().size() - 1);
			}
		});
		return app;
	}

	public SdkResult generateSdk(String collectionId, String language) {
		RequestCollection collection = workspaceService.exportCollection(collectionId);
		String lang = language == null || language.isBlank() ? "typescript" : language.toLowerCase();
		String source = "python".equals(lang) ? python(collection) : typescript(collection);
		String filename = slug(collection.getName()) + ("python".equals(lang) ? "_client.py" : "Client.ts");
		return new SdkResult(filename, source);
	}

	public String readPublic(String name) {
		if (name == null || name.contains("..") || name.contains("/") || name.contains("\\")) {
			throw new IllegalArgumentException("Invalid public file");
		}
		Path file = store.root().resolve("public").resolve(name);
		if (!Files.exists(file)) {
			throw new IllegalArgumentException("Public file not found");
		}
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not read public file");
		}
	}

	public Path writeStandalone(String name, String html) {
		try {
			Path dir = store.root().resolve("public");
			Files.createDirectories(dir);
			Path file = dir.resolve(name);
			Files.writeString(file, html, StandardCharsets.UTF_8);
			return file;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not write standalone file");
		}
	}

	private ApiRequest matchRequest(CapturedCall call) {
		String path = pathOf(call.getUrl());
		for (RequestCollection collection : store.copy().getCollections()) {
			for (ApiRequest request : collection.getRequests()) {
				if (call.getMethod() != null && request.getMethod().equalsIgnoreCase(call.getMethod()) && pathOf(request.getUrl()).equals(path)) {
					return request;
				}
			}
		}
		return null;
	}

	private static String pathOf(String url) {
		if (url == null) {
			return "";
		}
		String value = url;
		int scheme = value.indexOf("://");
		if (scheme >= 0) {
			int slash = value.indexOf('/', scheme + 3);
			value = slash < 0 ? "/" : value.substring(slash);
		}
		int query = value.indexOf('?');
		return query < 0 ? value : value.substring(0, query);
	}

	private static String typescript(RequestCollection collection) {
		StringBuilder builder = new StringBuilder();
		builder.append("export class ").append(className(collection.getName())).append(" {\n");
		builder.append("  constructor(private baseUrl: string, private headers: Record<string, string> = {}) {}\n\n");
		for (ApiRequest request : collection.getRequests()) {
			builder.append("  async ").append(methodName(request.getName())).append("(body?: unknown) {\n");
			builder.append("    const response = await fetch(`${this.baseUrl}").append(pathOf(request.getUrl())).append("`, {\n");
			builder.append("      method: '").append(request.getMethod()).append("',\n");
			builder.append("      headers: { 'content-type': 'application/json', ...this.headers },\n");
			if (!"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod())) {
				builder.append("      body: body === undefined ? undefined : JSON.stringify(body),\n");
			}
			builder.append("    });\n");
			builder.append("    return response.json();\n  }\n\n");
		}
		builder.append("}\n");
		return builder.toString();
	}

	private static String python(RequestCollection collection) {
		StringBuilder builder = new StringBuilder();
		builder.append("import requests\n\nclass ").append(className(collection.getName())).append(":\n");
		builder.append("    def __init__(self, base_url, headers=None):\n        self.base_url = base_url.rstrip('/')\n        self.headers = headers or {}\n\n");
		for (ApiRequest request : collection.getRequests()) {
			builder.append("    def ").append(methodName(request.getName())).append("(self, body=None):\n");
			builder.append("        return requests.request('").append(request.getMethod()).append("', f'{self.base_url}").append(pathOf(request.getUrl())).append("', json=body, headers=self.headers).json()\n\n");
		}
		return builder.toString();
	}

	private void requireRole(String minimum) {
		List<WorkspaceMember> members = store.copy().getMembers();
		if (members.isEmpty()) {
			return;
		}
		WorkspaceMember actor = members.stream().filter(WorkspaceMember::isActive).findFirst().orElse(members.get(0));
		if (rank(actor.getRole()) < rank(minimum)) {
			throw new IllegalArgumentException("Active role " + actor.getRole() + " cannot perform this action");
		}
	}

	private static int rank(String role) {
		return switch (role == null ? "" : role.toLowerCase()) {
			case "owner" -> 3;
			case "editor" -> 2;
			case "viewer" -> 1;
			default -> 2;
		};
	}

	private static String className(String name) {
		String base = methodName(name);
		if (base.isBlank()) {
			return "ApiClient";
		}
		return Character.toUpperCase(base.charAt(0)) + base.substring(1) + "Client";
	}

	private static String methodName(String name) {
		StringBuilder builder = new StringBuilder();
		boolean upper = false;
		for (char character : (name == null ? "" : name).toCharArray()) {
			if (Character.isLetterOrDigit(character)) {
				builder.append(upper ? Character.toUpperCase(character) : Character.toLowerCase(character));
				upper = false;
			}
			else {
				upper = builder.length() > 0;
			}
		}
		return builder.isEmpty() ? "call" : builder.toString();
	}

	private static String slug(String name) {
		return methodName(name);
	}

	public List<RunReport> runs(String collectionId) {
		return store.copy().getCollectionRuns().stream()
			.filter(item -> collectionId == null || collectionId.isBlank() || collectionId.equals(item.getCollectionId()))
			.toList();
	}

	public MonitorRun publishMonitorFile(MonitorRun run) {
		if (run.getReportHtml() == null || run.getReportHtml().isBlank()) {
			return run;
		}
		Path file = writeStandalone("monitor-" + run.getId() + ".html", run.getReportHtml());
		run.setReportUrl("/public/" + file.getFileName());
		return run;
	}

	public record SdkResult(String filename, String source) {
	}

}
