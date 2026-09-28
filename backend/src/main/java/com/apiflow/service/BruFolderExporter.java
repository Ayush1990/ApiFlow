package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.Folder;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;

import tools.jackson.databind.ObjectMapper;

public final class BruFolderExporter {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private BruFolderExporter() {
	}

	public record BruFileEntry(String path, String content) {
	}

	public static List<BruFileEntry> export(RequestCollection collection) {
		List<BruFileEntry> files = new ArrayList<>();
		String root = safeName(collection.getName());
		files.add(new BruFileEntry(root + "/bruno.json", brunoJson(collection.getName())));
		files.add(new BruFileEntry(root + "/collection.bru", collectionMeta(collection)));
		for (KeyValue variable : collection.getVariables()) {
			if (variable.getKey() != null && !variable.getKey().isBlank()) {
				// vars embedded in collection.bru for now
			}
		}
		Map<String, String> folderPaths = folderPaths(collection);
		for (Folder folder : collection.getFolders()) {
			String path = folderPaths.get(folder.getId());
			if (path != null && !path.isBlank()) {
				files.add(new BruFileEntry(root + "/" + path + "/folder.bru", folderMeta(folder)));
			}
		}
		for (ApiRequest request : collection.getRequests()) {
			String folderPath = folderPaths.getOrDefault(request.getFolderId(), "");
			String fileName = safeName(request.getName()) + ".bru";
			String fullPath = folderPath.isBlank() ? root + "/" + fileName : root + "/" + folderPath + "/" + fileName;
			files.add(new BruFileEntry(fullPath, ExportService.BrunoExporter.request(request, folderPath)));
		}
		return files;
	}

	public static void syncToDisk(Path collectionDir, RequestCollection collection) throws IOException {
		for (BruFileEntry entry : export(collection)) {
			String relative = entry.path();
			int slash = relative.indexOf('/');
			if (slash >= 0) {
				relative = relative.substring(slash + 1);
			}
			Path target = collectionDir.resolve(relative);
			Files.createDirectories(target.getParent());
			Files.writeString(target, entry.content(), StandardCharsets.UTF_8);
		}
	}

	private static String brunoJson(String name) {
		try {
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("version", "1", "name", name == null ? "Collection" : name, "type", "collection"));
		}
		catch (Exception ex) {
			return "{\"version\":\"1\",\"name\":\"" + (name == null ? "Collection" : name) + "\",\"type\":\"collection\"}";
		}
	}

	private static String collectionMeta(RequestCollection collection) {
		StringBuilder out = new StringBuilder();
		out.append("meta {\n  name: ").append(escape(collection.getName())).append("\n  type: collection\n}\n");
		if (!collection.getVariables().isEmpty()) {
			out.append("\nvars {\n");
			for (KeyValue variable : collection.getVariables()) {
				if (variable.getKey() != null && !variable.getKey().isBlank()) {
					out.append("  ").append(variable.getKey()).append(": ").append(escape(variable.getValue())).append('\n');
				}
			}
			out.append("}\n");
		}
		if (collection.getPreRequestScript() != null && !collection.getPreRequestScript().isBlank()) {
			out.append("\nscript:pre-request {\n").append(collection.getPreRequestScript()).append("\n}\n");
		}
		if (collection.getPostResponseScript() != null && !collection.getPostResponseScript().isBlank()) {
			out.append("\nscript:post-response {\n").append(collection.getPostResponseScript()).append("\n}\n");
		}
		return out.toString();
	}

	private static String folderMeta(Folder folder) {
		StringBuilder out = new StringBuilder();
		out.append("meta {\n  name: ").append(escape(folder.getName())).append("\n  type: folder\n}\n");
		if (folder.getPreRequestScript() != null && !folder.getPreRequestScript().isBlank()) {
			out.append("\nscript:pre-request {\n").append(folder.getPreRequestScript()).append("\n}\n");
		}
		if (folder.getPostResponseScript() != null && !folder.getPostResponseScript().isBlank()) {
			out.append("\nscript:post-response {\n").append(folder.getPostResponseScript()).append("\n}\n");
		}
		return out.toString();
	}

	private static Map<String, String> folderPaths(RequestCollection collection) {
		Map<String, String> paths = new HashMap<>();
		for (Folder folder : collection.getFolders()) {
			paths.put(folder.getId(), folderPath(collection, folder.getId()));
		}
		return paths;
	}

	private static String folderPath(RequestCollection collection, String folderId) {
		if (folderId == null || folderId.isBlank()) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		String current = folderId;
		java.util.Set<String> seen = new java.util.HashSet<>();
		while (current != null && !current.isBlank() && !seen.contains(current)) {
			seen.add(current);
			Folder match = null;
			for (Folder folder : collection.getFolders()) {
				if (current.equals(folder.getId())) {
					match = folder;
					break;
				}
			}
			if (match == null) {
				break;
			}
			parts.add(0, safeName(match.getName()));
			current = match.getParentId();
		}
		return String.join("/", parts);
	}

	private static String safeName(String name) {
		String base = name == null || name.isBlank() ? "item" : name.trim();
		base = base.replaceAll("[^A-Za-z0-9._-]+", "-");
		return base.isBlank() ? "item" : base;
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains("\n") || value.contains("\"")) {
			return "`" + value.replace("`", "\\`") + "`";
		}
		return value;
	}

}
