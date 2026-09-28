package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.apiflow.store.FileStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class NpmPackageService {

	private final FileStore store;
	private final ObjectMapper mapper = new ObjectMapper();

	public NpmPackageService(FileStore store) {
		this.store = store;
	}

	public Path scriptsRoot() {
		Path dir = store.root().resolve("scripts");
		try {
			Files.createDirectories(dir);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not create scripts directory");
		}
		return dir;
	}

	public String packageJson() {
		Path file = scriptsRoot().resolve("package.json");
		if (!Files.exists(file)) {
			return defaultPackageJson();
		}
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not read package.json");
		}
	}

	public String savePackageJson(String content) {
		if (content == null || content.isBlank()) {
			content = defaultPackageJson();
		}
		try {
			mapper.readTree(content);
			Files.writeString(scriptsRoot().resolve("package.json"), content, StandardCharsets.UTF_8);
			return content;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid package.json");
		}
	}

	public InstallResult install() {
		Path dir = scriptsRoot();
		savePackageJson(packageJson());
		try {
			Process process = new ProcessBuilder("npm", "install", "--omit=dev")
				.directory(dir.toFile())
				.redirectErrorStream(true)
				.start();
			boolean finished = process.waitFor(120, TimeUnit.SECONDS);
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!finished) {
				process.destroyForcibly();
				throw new IllegalArgumentException("npm install timed out");
			}
			if (process.exitValue() != 0) {
				throw new IllegalArgumentException(output.isBlank() ? "npm install failed" : output);
			}
			return new InstallResult(listPackages(), output);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("npm is not available: " + ex.getMessage());
		}
	}

	public InstallResult add(String packageName) {
		if (packageName == null || packageName.isBlank()) {
			throw new IllegalArgumentException("Package name is required");
		}
		Path dir = scriptsRoot();
		try {
			Process process = new ProcessBuilder("npm", "install", packageName.trim(), "--save", "--omit=dev")
				.directory(dir.toFile())
				.redirectErrorStream(true)
				.start();
			boolean finished = process.waitFor(120, TimeUnit.SECONDS);
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!finished || process.exitValue() != 0) {
				throw new IllegalArgumentException(output.isBlank() ? "npm install failed" : output);
			}
			return new InstallResult(listPackages(), output);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("npm is not available: " + ex.getMessage());
		}
	}

	public List<String> listPackages() {
		Path file = scriptsRoot().resolve("package.json");
		if (!Files.exists(file)) {
			return List.of();
		}
		try {
			JsonNode root = mapper.readTree(file.toFile());
			List<String> names = new ArrayList<>();
			JsonNode deps = root.path("dependencies");
			if (deps.isObject()) {
				deps.properties().forEach(entry -> names.add(entry.getKey()));
			}
			return names;
		}
		catch (Exception ex) {
			return List.of();
		}
	}

	private static String defaultPackageJson() {
		return """
				{
				  "name": "apiflow-scripts",
				  "private": true,
				  "dependencies": {}
				}
				""";
	}

	public record InstallResult(List<String> packages, String output) {
	}

}
