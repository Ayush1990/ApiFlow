package com.apiflow.service;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImportService {

	private final ObjectMapper mapper = new ObjectMapper();
	private final OpenCollectionService openCollection;

	public ImportService(OpenCollectionService openCollection) {
		this.openCollection = openCollection;
	}

	public java.util.Map<String, Object> organizePostman(String content) {
		String cleaned = PostmanExportOrganizer.organize(content);
		java.util.Map<String, Object> result = new java.util.LinkedHashMap<>(PostmanExportOrganizer.summary(cleaned));
		result.put("content", cleaned);
		return result;
	}

	public java.util.List<java.util.Map<String, String>> previewPostmanScripts(String content) {
		JsonNode root = parse(content.trim().startsWith("{") ? content : PostmanExportOrganizer.organize(content));
		java.util.List<java.util.Map<String, String>> rows = new ArrayList<>();
		walkScriptPreview(root.path("item"), rows);
		return rows;
	}

	private void walkScriptPreview(JsonNode items, java.util.List<java.util.Map<String, String>> rows) {
		if (!items.isArray()) {
			return;
		}
		for (JsonNode item : items) {
			if (item.has("item")) {
				walkScriptPreview(item.path("item"), rows);
			}
			else if (item.has("request")) {
				java.util.Map<String, String> row = new java.util.LinkedHashMap<>();
				row.put("name", text(item.path("name"), "Request"));
				String pre = joinScript(item, "prerequest");
				String post = joinScript(item, "test");
				row.put("preRequest", pre);
				row.put("postResponse", post);
				row.put("translatedPre", PostmanScriptTranslator.translate(pre));
				row.put("translatedPost", PostmanScriptTranslator.translate(post));
				if (!pre.isBlank() || !post.isBlank()) {
					rows.add(row);
				}
			}
		}
	}

	private static String joinScript(JsonNode item, String listen) {
		if (!item.path("event").isArray()) {
			return "";
		}
		StringBuilder script = new StringBuilder();
		for (JsonNode event : item.path("event")) {
			if (!listen.equals(text(event.path("listen"), ""))) {
				continue;
			}
			if (!event.path("script").path("exec").isArray()) {
				continue;
			}
			for (JsonNode line : event.path("script").path("exec")) {
				script.append(line.asString("")).append("\n");
			}
		}
		return script.toString().trim();
	}

	public RequestCollection importDocument(String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("Import file is empty");
		}
		String trimmed = content.trim();
		if (trimmed.startsWith("curl ") || trimmed.startsWith("curl\n") || trimmed.equals("curl")) {
			return CurlParser.collection(trimmed);
		}
		if (openCollection.looksLike(trimmed)) {
			return openCollection.importYaml(trimmed);
		}
		if (BrunoParser.looksLike(trimmed) && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
			return BrunoParser.collection(trimmed);
		}
		if (trimmed.contains("wsdl:definitions") || trimmed.contains(":definitions") || trimmed.contains("<definitions")) {
			return WsdlImporter.importWsdl(trimmed);
		}
		JsonNode root = parse(trimmed);
		if (root.has("openapi") || root.has("swagger")) {
			return openApi(root);
		}
		if (root.has("item") && root.has("info")) {
			return postman(root);
		}
		if (root.has("resources") && root.path("resources").isArray()) {
			return insomnia(root);
		}
		if (root.has("requests")) {
			try {
				RequestCollection collection = mapper.treeToValue(root, RequestCollection.class);
				if (collection.getId() == null || collection.getId().isBlank()) {
					collection.setId(UUID.randomUUID().toString());
				}
				for (ApiRequest request : collection.getRequests()) {
					if (request.getId() == null || request.getId().isBlank()) {
						request.setId(UUID.randomUUID().toString());
					}
				}
				return collection;
			}
			catch (Exception ex) {
				throw new IllegalArgumentException("Could not read ApiFlow collection");
			}
		}
		throw new IllegalArgumentException("Use a Postman collection, OpenAPI document, Bruno file, Insomnia export, curl command, or ApiFlow export");
	}

	private JsonNode parse(String content) {
		try {
			if (content.startsWith("{") || content.startsWith("[")) {
				return mapper.readTree(content);
			}
			Object loaded = new Yaml().load(content);
			return mapper.valueToTree(loaded);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not parse the file as JSON or YAML");
		}
	}

	private RequestCollection postman(JsonNode root) {
		RequestCollection collection = new RequestCollection();
		collection.setId(UUID.randomUUID().toString());
		collection.setName(text(root.path("info").path("name"), "Imported Postman"));
		walkPostman(root.path("item"), collection, "", 0);
		return collection;
	}

	private int walkPostman(JsonNode items, RequestCollection collection, String folderId, int position) {
		if (!items.isArray()) {
			return position;
		}
		for (JsonNode item : items) {
			if (item.has("item")) {
				var folder = new com.apiflow.model.Folder();
				folder.setId(UUID.randomUUID().toString());
				folder.setName(text(item.path("name"), "Folder"));
				folder.setParentId(folderId);
				folder.setPosition(position++);
				collection.getFolders().add(folder);
				position = walkPostman(item.path("item"), collection, folder.getId(), position);
			}
			else if (item.has("request")) {
				collection.getRequests().add(postmanRequest(item, folderId, position++));
			}
		}
		return position;
	}

	private ApiRequest postmanRequest(JsonNode item, String folderId, int position) {
		JsonNode request = item.path("request");
		ApiRequest apiRequest = new ApiRequest();
		apiRequest.setId(UUID.randomUUID().toString());
		apiRequest.setName(text(item.path("name"), "Request"));
		apiRequest.setFolderId(folderId);
		apiRequest.setPosition(position);
		apiRequest.setMethod(text(request.path("method"), "GET"));
		JsonNode url = request.path("url");
		apiRequest.setUrl(url.isTextual() ? url.asString() : text(url.path("raw"), ""));
		if (request.path("header").isArray()) {
			for (JsonNode header : request.path("header")) {
				apiRequest.getHeaders().add(new KeyValue(text(header.path("key"), ""), text(header.path("value"), ""), !header.path("disabled").asBoolean(false)));
			}
		}
		JsonNode body = request.path("body");
		String mode = text(body.path("mode"), "");
		if ("raw".equals(mode)) {
			apiRequest.setBodyType("json");
			apiRequest.setBody(text(body.path("raw"), ""));
		}
		else if ("urlencoded".equals(mode) || "formdata".equals(mode)) {
			apiRequest.setBodyType("formdata".equals(mode) ? "multipart" : "form");
			if (body.path(mode).isArray()) {
				for (JsonNode field : body.path(mode)) {
					if (!"file".equals(text(field.path("type"), ""))) {
						apiRequest.getForm().add(new KeyValue(text(field.path("key"), ""), text(field.path("value"), ""), !field.path("disabled").asBoolean(false)));
					}
				}
			}
		}
		JsonNode auth = request.path("auth");
		String authType = text(auth.path("type"), "none");
		if ("bearer".equals(authType)) {
			apiRequest.setAuthType("bearer");
			apiRequest.setAuthToken(authValue(auth.path("bearer"), "token"));
		}
		else if ("basic".equals(authType)) {
			apiRequest.setAuthType("basic");
			apiRequest.setAuthUsername(authValue(auth.path("basic"), "username"));
			apiRequest.setAuthPassword(authValue(auth.path("basic"), "password"));
		}
		else if ("apikey".equals(authType)) {
			apiRequest.setAuthType("apikey");
			apiRequest.setApiKeyName(authValue(auth.path("apikey"), "key"));
			apiRequest.setApiKeyValue(authValue(auth.path("apikey"), "value"));
			apiRequest.setApiKeyIn(authValue(auth.path("apikey"), "in"));
		}
		String pre = joinScript(item, "prerequest");
		String post = joinScript(item, "test");
		if (!pre.isBlank()) {
			apiRequest.setPreRequestScript(PostmanScriptTranslator.translate(pre));
		}
		if (!post.isBlank()) {
			apiRequest.setPostResponseScript(PostmanScriptTranslator.translate(post));
		}
		return apiRequest;
	}

	private RequestCollection openApi(JsonNode root) {
		RequestCollection collection = new RequestCollection();
		collection.setId(UUID.randomUUID().toString());
		collection.setName(text(root.path("info").path("title"), "Imported OpenAPI"));
		String server = "";
		if (root.path("servers").isArray() && !root.path("servers").isEmpty()) {
			server = text(root.path("servers").get(0).path("url"), "");
		}
		if (!server.isBlank()) {
			collection.getVariables().add(new KeyValue("baseUrl", server, true));
		}
		int position = 0;
		JsonNode paths = root.path("paths");
		if (paths.isObject()) {
			var fields = new ArrayList<Map.Entry<String, JsonNode>>();
			paths.properties().forEach(entry -> fields.add(entry));
			for (Map.Entry<String, JsonNode> entry : fields) {
				for (String method : new String[] {"get", "post", "put", "patch", "delete", "head", "options"}) {
					JsonNode operation = entry.getValue().path(method);
					if (operation.isMissingNode() || operation.isNull()) {
						continue;
					}
					ApiRequest request = new ApiRequest();
					request.setId(UUID.randomUUID().toString());
					String summary = text(operation.path("summary"), "");
					request.setName(summary.isBlank() ? method.toUpperCase() + " " + entry.getKey() : summary);
					request.setMethod(method.toUpperCase());
					request.setUrl((server.isBlank() ? "" : "{{baseUrl}}") + entry.getKey());
					request.setPosition(position++);
					collection.getRequests().add(request);
				}
			}
		}
		return collection;
	}

	private RequestCollection insomnia(JsonNode root) {
		RequestCollection collection = new RequestCollection();
		collection.setId(UUID.randomUUID().toString());
		collection.setName("Imported Insomnia");
		java.util.Map<String, String> folders = new java.util.HashMap<>();
		JsonNode resources = root.path("resources");
		for (JsonNode resource : resources) {
			String type = text(resource.path("_type"), "");
			if ("workspace".equals(type)) {
				collection.setName(text(resource.path("name"), collection.getName()));
			}
			else if ("request_group".equals(type)) {
				com.apiflow.model.Folder folder = new com.apiflow.model.Folder();
				folder.setId(UUID.randomUUID().toString());
				folder.setName(text(resource.path("name"), "Folder"));
				folder.setPosition(collection.getFolders().size());
				folders.put(text(resource.path("_id"), folder.getId()), folder.getId());
				collection.getFolders().add(folder);
			}
		}
		for (JsonNode resource : resources) {
			if (!"request_group".equals(text(resource.path("_type"), ""))) {
				continue;
			}
			String parent = folders.get(text(resource.path("parentId"), ""));
			for (com.apiflow.model.Folder folder : collection.getFolders()) {
				if (folder.getId().equals(folders.get(text(resource.path("_id"), "")))) {
					folder.setParentId(parent == null ? "" : parent);
				}
			}
		}
		int position = 0;
		for (JsonNode resource : resources) {
			if (!"request".equals(text(resource.path("_type"), ""))) {
				continue;
			}
			ApiRequest request = new ApiRequest();
			request.setId(UUID.randomUUID().toString());
			request.setName(text(resource.path("name"), "Request"));
			request.setMethod(text(resource.path("method"), "GET"));
			request.setUrl(text(resource.path("url"), ""));
			request.setPosition(position++);
			String folderId = folders.get(text(resource.path("parentId"), ""));
			request.setFolderId(folderId == null ? "" : folderId);
			if (resource.path("headers").isArray()) {
				for (JsonNode header : resource.path("headers")) {
					String key = text(header.path("name"), text(header.path("key"), ""));
					request.getHeaders().add(new KeyValue(key, text(header.path("value"), ""), !header.path("disabled").asBoolean(false)));
				}
			}
			JsonNode body = resource.path("body");
			String mime = text(body.path("mimeType"), "");
			String textBody = text(body.path("text"), "");
			if (!textBody.isBlank()) {
				request.setBody(textBody);
				request.setBodyType(mime.contains("json") ? "json" : "text");
			}
			JsonNode authentication = resource.path("authentication");
			String authType = text(authentication.path("type"), "none");
			if ("bearer".equals(authType)) {
				request.setAuthType("bearer");
				request.setAuthToken(text(authentication.path("token"), ""));
			}
			else if ("basic".equals(authType)) {
				request.setAuthType("basic");
				request.setAuthUsername(text(authentication.path("username"), ""));
				request.setAuthPassword(text(authentication.path("password"), ""));
			}
			collection.getRequests().add(request);
		}
		if (collection.getRequests().isEmpty() && collection.getFolders().isEmpty()) {
			throw new IllegalArgumentException("Insomnia export has no requests");
		}
		return collection;
	}

	private static String authValue(JsonNode list, String key) {
		if (!list.isArray()) {
			return "";
		}
		for (JsonNode item : list) {
			if (key.equals(text(item.path("key"), ""))) {
				return text(item.path("value"), "");
			}
		}
		return "";
	}

	private static String text(JsonNode node, String fallback) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return fallback;
		}
		String value = node.asString();
		return value == null || value.isBlank() ? fallback : value;
	}

}
