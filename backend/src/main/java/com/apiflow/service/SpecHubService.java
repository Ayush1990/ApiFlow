package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.apiflow.model.ApiSpec;
import com.apiflow.model.ChangeResult;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

@Service
public class SpecHubService {

	private final FileStore store;
	private final OpenApiSyncService openApiSyncService;

	public SpecHubService(FileStore store, OpenApiSyncService openApiSyncService) {
		this.store = store;
		this.openApiSyncService = openApiSyncService;
	}

	public List<ApiSpec> list() {
		return store.copy().getSpecs();
	}

	public ApiSpec get(String id) {
		return store.copy().getSpecs().stream().filter(item -> item.getId().equals(id)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Spec not found"));
	}

	public ChangeResult save(ApiSpec spec) {
		if (spec.getId() == null || spec.getId().isBlank()) {
			spec.setId(UUID.randomUUID().toString());
		}
		spec.setUpdatedAt(System.currentTimeMillis());
		String id = spec.getId();
		Workspace workspace = store.update(data -> {
			data.getSpecs().removeIf(item -> item.getId().equals(id));
			data.getSpecs().add(spec);
		});
		ActivityLog.add(store, "spec", "Updated spec " + spec.getName());
		return new ChangeResult(workspace, id);
	}

	private static String bundle(ApiSpec spec) {
		StringBuilder builder = new StringBuilder(spec.getContent() == null ? "" : spec.getContent());
		for (ApiSpec.SpecFile file : spec.getFiles()) {
			builder.append("\n# file: ").append(file.getPath()).append('\n').append(file.getContent() == null ? "" : file.getContent());
		}
		return builder.toString();
	}

	private List<GovernanceIssue> checkOpenApi(ApiSpec spec) {
		List<GovernanceIssue> issues = new ArrayList<>();
		issues.addAll(checkDocument(spec.getContent(), "main"));
		for (ApiSpec.SpecFile file : spec.getFiles()) {
			issues.addAll(checkDocument(file.getContent(), file.getPath()));
		}
		java.util.regex.Matcher refs = Pattern.compile("['\"]?([^\\s'\"#]+\\.(?:yaml|yml|json))(?:#[^'\"\\s]*)?['\"]?").matcher(bundle(spec));
		while (refs.find()) {
			String path = refs.group(1);
			boolean found = spec.getFiles().stream().anyMatch(file -> path.equals(file.getPath()) || file.getPath().endsWith("/" + path));
			if (!found) {
				issues.add(new GovernanceIssue("error", "oas3-ref: missing component file " + path));
			}
		}
		return issues;
	}

	@SuppressWarnings("unchecked")
	private List<GovernanceIssue> checkDocument(String content, String name) {
		List<GovernanceIssue> issues = new ArrayList<>();
		if (content == null || content.isBlank()) {
			return issues;
		}
		Object loaded;
		try {
			if (content.trim().startsWith("{")) {
				loaded = new tools.jackson.databind.ObjectMapper().readValue(content, java.util.Map.class);
			}
			else {
				loaded = new org.yaml.snakeyaml.Yaml().load(content);
			}
		}
		catch (Exception ex) {
			issues.add(new GovernanceIssue("error", name + " oas3-parse: could not parse this file"));
			return issues;
		}
		if (!(loaded instanceof java.util.Map<?, ?> root)) {
			return issues;
		}
		Object info = root.get("info");
		if (!(info instanceof java.util.Map<?, ?> infoMap)) {
			if (root.containsKey("paths") || root.containsKey("openapi") || root.containsKey("swagger")) {
				issues.add(new GovernanceIssue("error", name + " info-required: info section is required"));
			}
		}
		else {
			if (text(infoMap.get("title")).isBlank()) {
				issues.add(new GovernanceIssue("error", name + " info-title: title is required"));
			}
			if (text(infoMap.get("version")).isBlank()) {
				issues.add(new GovernanceIssue("error", name + " info-version: version is required"));
			}
			if (text(infoMap.get("description")).isBlank()) {
				issues.add(new GovernanceIssue("warning", name + " info-description: add a description"));
			}
		}
		Object servers = root.get("servers");
		if (servers instanceof List<?> serverList) {
			for (Object server : serverList) {
				if (server instanceof java.util.Map<?, ?> serverMap && text(serverMap.get("url")).startsWith("http://")) {
					issues.add(new GovernanceIssue("warning", name + " oas3-server-https: use HTTPS URLs"));
				}
			}
		}
		Object paths = root.get("paths");
		if (paths instanceof java.util.Map<?, ?> pathMap) {
			java.util.Set<String> operationIds = new java.util.HashSet<>();
			for (java.util.Map.Entry<?, ?> pathEntry : pathMap.entrySet()) {
				if (!(pathEntry.getValue() instanceof java.util.Map<?, ?> operations)) {
					continue;
				}
				for (java.util.Map.Entry<?, ?> operationEntry : operations.entrySet()) {
					String method = String.valueOf(operationEntry.getKey());
					if (!List.of("get", "post", "put", "patch", "delete", "head", "options").contains(method)) {
						continue;
					}
					if (!(operationEntry.getValue() instanceof java.util.Map<?, ?> operation)) {
						continue;
					}
					String pointer = name + " " + method.toUpperCase() + " " + pathEntry.getKey();
					String operationId = text(operation.get("operationId"));
					if (operationId.isBlank()) {
						issues.add(new GovernanceIssue("warning", pointer + " operation-operationId: declare operationId"));
					}
					else if (!operationIds.add(operationId)) {
						issues.add(new GovernanceIssue("error", pointer + " operation-operationId-unique: duplicate operationId " + operationId));
					}
					if (text(operation.get("description")).isBlank() && text(operation.get("summary")).isBlank()) {
						issues.add(new GovernanceIssue("warning", pointer + " operation-description: add a summary or description"));
					}
					if (!(operation.get("responses") instanceof java.util.Map<?, ?> responses) || responses.isEmpty()) {
						issues.add(new GovernanceIssue("error", pointer + " operation-responses: declare responses"));
					}
					else {
						boolean success = responses.keySet().stream().anyMatch(code -> String.valueOf(code).startsWith("2"));
						if (!success) {
							issues.add(new GovernanceIssue("warning", pointer + " operation-success-response: add a 2xx response"));
						}
						for (Object response : responses.values()) {
							if (response instanceof java.util.Map<?, ?> responseMap && responseMap.get("content") instanceof java.util.Map<?, ?> contentMap) {
								contentMap.values().forEach(media -> {
									if (media instanceof java.util.Map<?, ?> mediaMap && !mediaMap.containsKey("schema")) {
										issues.add(new GovernanceIssue("info", pointer + " operation-response-schema: response content should declare a schema"));
									}
								});
							}
						}
					}
					if (operation.get("parameters") instanceof List<?> parameters) {
						java.util.Set<String> names = new java.util.HashSet<>();
						for (Object parameter : parameters) {
							if (parameter instanceof java.util.Map<?, ?> parameterMap) {
								String key = text(parameterMap.get("in")) + ":" + text(parameterMap.get("name"));
								if (!names.add(key)) {
									issues.add(new GovernanceIssue("error", pointer + " operation-parameters: duplicate parameter " + key));
								}
							}
						}
					}
				}
			}
		}
		return issues;
	}

	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	public ChangeResult delete(String id) {
		Workspace workspace = store.update(data -> data.getSpecs().removeIf(item -> item.getId().equals(id)));
		return new ChangeResult(workspace, "");
	}

	public GovernanceReport lint(String id) {
		ApiSpec spec = get(id);
		List<GovernanceIssue> issues = new ArrayList<>();
		String content = bundle(spec);
		String format = spec.getFormat().toLowerCase();
		if (content == null || content.isBlank()) {
			issues.add(new GovernanceIssue("error", "Spec content is empty"));
			return new GovernanceReport(issues);
		}
		if ("openapi".equals(format) || "asyncapi".equals(format)) {
			issues.addAll(checkOpenApi(spec));
		}
		if ("protobuf".equals(format) || format.startsWith("proto")) {
			if (!content.contains("message ") && !content.contains("service ")) {
				issues.add(new GovernanceIssue("warning", "No message or service definitions found"));
			}
		}
		if ("graphql".equals(format) && !content.contains("type ")) {
			issues.add(new GovernanceIssue("warning", "No GraphQL type definitions found"));
		}
		if ("smithy".equals(format) && !content.contains("namespace")) {
			issues.add(new GovernanceIssue("warning", "Smithy namespace is recommended"));
		}
		return new GovernanceReport(issues);
	}

	public String previewHtml(String id) {
		ApiSpec spec = get(id);
		String escaped = escape(bundle(spec));
		return """
			<!doctype html><html><head><meta charset="utf-8"><title>%s</title>
			<style>body{font-family:system-ui,sans-serif;margin:24px;background:#0f1117;color:#e8eaed}
			pre{background:#171923;padding:16px;border-radius:12px;overflow:auto;white-space:pre-wrap}
			.badge{display:inline-block;padding:4px 10px;border-radius:999px;background:#303134;font-size:12px}
			</style></head><body>
			<h1>%s <span class="badge">%s</span></h1>
			<p>Live preview generated from Spec Hub.</p>
			<pre>%s</pre></body></html>
			""".formatted(escape(spec.getName()), escape(spec.getName()), escape(spec.getFormat()), escaped);
	}

	public String exportFromCollection(com.apiflow.model.RequestCollection collection) {
		return openApiSyncService.exportMerged(collection);
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public record GovernanceIssue(String level, String message) {
	}

	public record GovernanceReport(List<GovernanceIssue> issues) {
	}

}
