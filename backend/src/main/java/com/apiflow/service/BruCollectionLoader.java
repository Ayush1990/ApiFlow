package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.Folder;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class BruCollectionLoader {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private BruCollectionLoader() {
	}

	public static boolean hasBruTree(Path collectionDir) throws IOException {
		if (!Files.isDirectory(collectionDir)) {
			return false;
		}
		if (Files.exists(collectionDir.resolve("bruno.json")) || Files.exists(collectionDir.resolve("collection.bru"))) {
			return true;
		}
		try (Stream<Path> walk = Files.walk(collectionDir)) {
			return walk.anyMatch(path -> path.getFileName().toString().endsWith(".bru"));
		}
	}

	public static RequestCollection load(Path collectionDir, String collectionId) throws IOException {
		RequestCollection collection = new RequestCollection();
		collection.setId(collectionId == null || collectionId.isBlank() ? collectionDir.getFileName().toString() : collectionId);
		collection.setName(collectionDir.getFileName().toString());
		Path brunoJson = collectionDir.resolve("bruno.json");
		if (Files.exists(brunoJson)) {
			try {
				JsonNode node = MAPPER.readTree(brunoJson.toFile());
				String name = node.path("name").asString();
				if (name != null && !name.isBlank()) {
					collection.setName(name);
				}
			}
			catch (Exception ignored) {
			}
		}
		Path collectionBru = collectionDir.resolve("collection.bru");
		if (Files.exists(collectionBru)) {
			BrunoParser.applyCollectionMeta(collection, Files.readString(collectionBru, StandardCharsets.UTF_8));
		}
		Map<String, String> folderIds = new HashMap<>();
		List<Path> bruFiles = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(collectionDir)) {
			walk.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".bru"))
				.filter(path -> !path.getFileName().toString().equals("collection.bru"))
				.forEach(bruFiles::add);
		}
		bruFiles.sort(Comparator.comparing(Path::toString));
		for (Path file : bruFiles) {
			String relative = collectionDir.relativize(file).toString().replace('\\', '/');
			String parent = parentPath(relative);
			if (parent != null && !parent.isBlank()) {
				ensureFolders(collection, folderIds, parent);
			}
		}
		for (Path file : bruFiles) {
			String content = Files.readString(file, StandardCharsets.UTF_8);
			if (BrunoParser.isFolder(content)) {
				String relative = collectionDir.relativize(file).toString().replace('\\', '/');
				String folderPath = parentPath(relative);
				String id = folderIds.get(folderPath);
				if (id != null) {
					BrunoParser.applyFolderMeta(findFolder(collection, id), content);
				}
				continue;
			}
			ApiRequest request = BrunoParser.request(content);
			String relative = collectionDir.relativize(file).toString().replace('\\', '/');
			request.setFolderId(folderIds.getOrDefault(parentPath(relative), ""));
			collection.getRequests().add(request);
		}
		collection.getRequests().sort(Comparator.comparingInt(ApiRequest::getPosition));
		for (int index = 0; index < collection.getRequests().size(); index++) {
			collection.getRequests().get(index).setPosition(index);
		}
		return collection;
	}

	private static void ensureFolders(RequestCollection collection, Map<String, String> folderIds, String directory) {
		if (directory == null || directory.isBlank()) {
			return;
		}
		String[] parts = directory.split("/");
		String current = "";
		String parentId = "";
		for (String part : parts) {
			if (part.isBlank() || "folder.bru".equals(part)) {
				continue;
			}
			current = current.isEmpty() ? part : current + "/" + part;
			if (!folderIds.containsKey(current)) {
				Folder folder = new Folder();
				folder.setId(java.util.UUID.randomUUID().toString());
				folder.setName(part);
				folder.setParentId(parentId);
				folder.setPosition(collection.getFolders().size());
				collection.getFolders().add(folder);
				folderIds.put(current, folder.getId());
			}
			parentId = folderIds.get(current);
		}
	}

	private static Folder findFolder(RequestCollection collection, String id) {
		for (Folder folder : collection.getFolders()) {
			if (id.equals(folder.getId())) {
				return folder;
			}
		}
		return null;
	}

	private static String parentPath(String relative) {
		int slash = relative.lastIndexOf('/');
		if (slash < 0) {
			return "";
		}
		return relative.substring(0, slash);
	}

}
