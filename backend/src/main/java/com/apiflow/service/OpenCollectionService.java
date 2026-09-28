package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;

@Service
public class OpenCollectionService {

	public RequestCollection importYaml(String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("OpenCollection YAML is empty");
		}
		Object loaded = new Yaml().load(content);
		if (!(loaded instanceof Map<?, ?> root)) {
			throw new IllegalArgumentException("OpenCollection YAML must be a mapping");
		}
		RequestCollection collection = new RequestCollection();
		collection.setId(UUID.randomUUID().toString());
		collection.setName(text(root.get("name"), "Imported OpenCollection"));
		collection.setDocs(text(root.get("description"), ""));
		Object variables = root.get("variables");
		if (variables instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				collection.getVariables().add(new KeyValue(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()), true));
			}
		}
		Object requests = root.get("requests");
		if (requests instanceof List<?> list) {
			int position = 0;
			for (Object item : list) {
				if (item instanceof Map<?, ?> map) {
					collection.getRequests().add(fromMap(map, position++));
				}
			}
		}
		return collection;
	}

	public String exportYaml(RequestCollection collection) {
		Map<String, Object> root = new java.util.LinkedHashMap<>();
		root.put("opencollection", "1.0");
		root.put("name", collection.getName());
		if (!collection.getDocs().isBlank()) {
			root.put("description", collection.getDocs());
		}
		if (!collection.getVariables().isEmpty()) {
			Map<String, String> vars = new java.util.LinkedHashMap<>();
			for (KeyValue variable : collection.getVariables()) {
				if (variable.getKey() != null && !variable.getKey().isBlank()) {
					vars.put(variable.getKey(), variable.getValue() == null ? "" : variable.getValue());
				}
			}
			root.put("variables", vars);
		}
		List<Map<String, Object>> requests = new ArrayList<>();
		for (ApiRequest request : collection.getRequests()) {
			Map<String, Object> item = new java.util.LinkedHashMap<>();
			item.put("name", request.getName());
			item.put("method", request.getMethod() == null ? "GET" : request.getMethod());
			item.put("url", request.getUrl());
			item.put("bodyType", request.getBodyType() == null ? "none" : request.getBodyType());
			if (!request.getBody().isBlank()) {
				item.put("body", request.getBody());
			}
			if (!request.getDocs().isBlank()) {
				item.put("description", request.getDocs());
			}
			if (!request.getPreRequestScript().isBlank()) {
				item.put("preRequestScript", request.getPreRequestScript());
			}
			if (!request.getPostResponseScript().isBlank()) {
				item.put("postResponseScript", request.getPostResponseScript());
			}
			List<Map<String, String>> headers = new ArrayList<>();
			for (KeyValue header : request.getHeaders()) {
				if (header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
					headers.add(Map.of("name", header.getKey(), "value", header.getValue() == null ? "" : header.getValue()));
				}
			}
			if (!headers.isEmpty()) {
				item.put("headers", headers);
			}
			requests.add(item);
		}
		root.put("requests", requests);
		return new Yaml().dump(root);
	}

	public boolean looksLike(String content) {
		if (content == null || content.isBlank()) {
			return false;
		}
		String trimmed = content.trim();
		return trimmed.contains("opencollection:") || (trimmed.contains("requests:") && trimmed.contains("name:") && !trimmed.startsWith("{"));
	}

	private ApiRequest fromMap(Map<?, ?> map, int position) {
		ApiRequest request = new ApiRequest();
		request.setId(UUID.randomUUID().toString());
		request.setName(text(map.get("name"), "Request"));
		request.setMethod(text(map.get("method"), "GET"));
		request.setUrl(text(map.get("url"), ""));
		request.setBodyType(text(map.get("bodyType"), "none"));
		request.setBody(text(map.get("body"), ""));
		request.setDocs(text(map.get("description"), ""));
		request.setPreRequestScript(text(map.get("preRequestScript"), ""));
		request.setPostResponseScript(text(map.get("postResponseScript"), ""));
		request.setPosition(position);
		Object headers = map.get("headers");
		if (headers instanceof List<?> list) {
			for (Object row : list) {
				if (row instanceof Map<?, ?> header) {
					request.getHeaders().add(new KeyValue(text(header.get("name"), ""), text(header.get("value"), ""), true));
				}
			}
		}
		return request;
	}

	private static String text(Object value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String text = String.valueOf(value).trim();
		return text.isBlank() ? fallback : text;
	}

}
