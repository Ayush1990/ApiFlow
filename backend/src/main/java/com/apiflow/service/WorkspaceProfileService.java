package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

import tools.jackson.databind.ObjectMapper;

@Service
public class WorkspaceProfileService {

	private final FileStore store;
	private final WorkspaceBundleService bundleService;
	private final ObjectMapper mapper = new ObjectMapper();

	public WorkspaceProfileService(FileStore store, WorkspaceBundleService bundleService) {
		this.store = store;
		this.bundleService = bundleService;
	}

	public List<String> list() {
		List<String> names = new ArrayList<>();
		Path dir = profilesDir();
		if (!Files.isDirectory(dir)) {
			return names;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
			for (Path file : stream) {
				names.add(file.getFileName().toString().replace(".json", ""));
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not list workspace profiles");
		}
		return names;
	}

	public void saveCurrent(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Profile name is required");
		}
		try {
			Files.createDirectories(profilesDir());
			Files.writeString(profilesDir().resolve(safeName(name) + ".json"), bundleService.exportBundle(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not save workspace profile");
		}
	}

	public Workspace load(String name) {
		Path file = profilesDir().resolve(safeName(name) + ".json");
		if (!Files.exists(file)) {
			throw new NotFoundException("Profile not found");
		}
		try {
			String content = Files.readString(file, StandardCharsets.UTF_8);
			return bundleService.importBundle(content);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not load workspace profile");
		}
	}

	private Path profilesDir() {
		return store.root().resolve("profiles");
	}

	private static String safeName(String name) {
		return name.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
	}

}
