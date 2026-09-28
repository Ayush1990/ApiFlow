package com.apiflow.store;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.Workspace;
import com.apiflow.service.BruCollectionLoader;
import com.apiflow.service.BruDiskSync;

import tools.jackson.databind.ObjectMapper;

@Component
public class FileStore {

	private final ObjectMapper mapper = new ObjectMapper();
	private final Path path;
	private final Path root;
	private final Path collectionsDir;
	private final Path indexFile;
	private Workspace data;
	private long loadedAt;
	private volatile boolean internalWrite;

	public FileStore(@Value("${apiflow.data-file:data/apiflow.json}") String path) {
		this.path = Path.of(path);
		this.root = this.path.getParent() == null ? Path.of(".") : this.path.getParent();
		this.collectionsDir = root.resolve("collections");
		this.indexFile = root.resolve("workspace.json");
		load();
	}

	public Path root() {
		return root;
	}

	public synchronized Workspace copy() {
		reloadIfChanged();
		return deepCopy(data);
	}

	public synchronized void reloadFromDiskIfExternal() {
		if (internalWrite) {
			return;
		}
		if (data.getSettings() == null || !data.getSettings().isNativeBruStorage()) {
			return;
		}
		try {
			if (hasCollectionFiles()) {
				data = readFromFiles();
				preferInheritOnSamples(data);
				loadedAt = System.currentTimeMillis();
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not reload workspace from disk", ex);
		}
	}

	public synchronized Workspace update(Consumer<Workspace> action) {
		Workspace next = copy();
		action.accept(next);
		data = next;
		persist();
		return deepCopy(data);
	}

	private void load() {
		try {
			if (hasCollectionFiles()) {
				data = readFromFiles();
				if (!Files.exists(indexFile)) {
					persist();
				}
			}
			else if (Files.exists(path)) {
				data = mapper.readValue(path.toFile(), Workspace.class);
				if (data == null) {
					data = SeedData.workspace();
				}
				persist();
			}
			else {
				data = SeedData.workspace();
				persist();
			}
			preferInheritOnSamples(data);
			loadedAt = System.currentTimeMillis();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not read workspace from " + root, ex);
		}
	}

	private void reloadIfChanged() {
		try {
			if (newestStamp() > loadedAt) {
				data = hasCollectionFiles() ? readFromFiles() : data;
				preferInheritOnSamples(data);
				loadedAt = System.currentTimeMillis();
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not reload workspace from disk", ex);
		}
	}

	private void persist() {
		internalWrite = true;
		try {
			Files.createDirectories(collectionsDir);
			WorkspaceIndex index = new WorkspaceIndex();
			index.setEnvironments(data.getEnvironments());
			index.setCookies(data.getCookies());
			index.setHistory(data.getHistory());
			index.setVariables(data.getVariables());
			index.setSettings(data.getSettings());
			Set<String> collectionIds = new HashSet<>();
			for (RequestCollection collection : data.getCollections()) {
				collectionIds.add(collection.getId());
				index.getCollectionIds().add(collection.getId());
				writeCollection(collection);
			}
			writeJson(indexFile, index);
			if (data.getSettings() == null || !data.getSettings().isNativeBruStorage()) {
				writeJson(path, data);
			}
			deleteMissing(collectionsDir, collectionIds, false);
			loadedAt = System.currentTimeMillis();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not save workspace to " + root, ex);
		}
		finally {
			internalWrite = false;
		}
	}

	private void writeCollection(RequestCollection collection) throws IOException {
		Path dir = collectionsDir.resolve(collection.getId());
		Path requests = dir.resolve("requests");
		Files.createDirectories(requests);
		RequestCollection meta = new RequestCollection();
		meta.setId(collection.getId());
		meta.setName(collection.getName());
		meta.setVariables(collection.getVariables());
		meta.setFolders(collection.getFolders());
		meta.setDefaults(collection.getDefaults());
		meta.setDocs(collection.getDocs());
		meta.setPreRequestScript(collection.getPreRequestScript());
		meta.setPostResponseScript(collection.getPostResponseScript());
		meta.setOpenApiSpec(collection.getOpenApiSpec());
		writeJson(dir.resolve("collection.json"), meta);
		boolean nativeBru = data.getSettings() != null && data.getSettings().isNativeBruStorage();
		if (nativeBru) {
			BruDiskSync.syncCollection(dir, collection);
			cleanLegacyRequestJson(requests);
			return;
		}
		Set<String> names = new HashSet<>();
		for (ApiRequest request : collection.getRequests()) {
			String fileName = RequestFiles.name(request);
			names.add(fileName);
			writeJson(requests.resolve(fileName), request);
		}
		deleteMissing(requests, names, true);
	}

	private void cleanLegacyRequestJson(Path requests) throws IOException {
		if (!Files.isDirectory(requests)) {
			return;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(requests, "*.json")) {
			for (Path file : files) {
				Files.deleteIfExists(file);
			}
		}
	}

	private Workspace readFromFiles() throws IOException {
		Workspace workspace = new Workspace();
		if (Files.exists(indexFile)) {
			WorkspaceIndex index = mapper.readValue(indexFile.toFile(), WorkspaceIndex.class);
			if (index.getEnvironments() != null) {
				workspace.setEnvironments(index.getEnvironments());
			}
			if (index.getCookies() != null) {
				workspace.setCookies(index.getCookies());
			}
			if (index.getHistory() != null) {
				workspace.setHistory(index.getHistory());
			}
			if (index.getVariables() != null) {
				workspace.setVariables(index.getVariables());
			}
			if (index.getSettings() != null) {
				workspace.setSettings(index.getSettings());
			}
			boolean nativeBru = index.getSettings() != null && index.getSettings().isNativeBruStorage();
			for (String id : index.getCollectionIds()) {
				RequestCollection collection = readCollection(collectionsDir.resolve(id), nativeBru);
				if (collection != null) {
					workspace.getCollections().add(collection);
				}
			}
		}
		else if (Files.exists(path)) {
			Workspace legacy = mapper.readValue(path.toFile(), Workspace.class);
			if (legacy != null) {
				workspace.setEnvironments(legacy.getEnvironments());
				workspace.setCookies(legacy.getCookies());
				workspace.setHistory(legacy.getHistory());
			}
		}
		if (!Files.isDirectory(collectionsDir)) {
			return workspace;
		}
		try (DirectoryStream<Path> dirs = Files.newDirectoryStream(collectionsDir)) {
			for (Path dir : dirs) {
				if (!Files.isDirectory(dir)) {
					continue;
				}
				boolean known = workspace.getCollections().stream().anyMatch(collection -> dir.getFileName().toString().equals(collection.getId()));
				if (!known) {
					boolean nativeBru = workspace.getSettings() != null && workspace.getSettings().isNativeBruStorage();
					RequestCollection collection = readCollection(dir, nativeBru);
					if (collection != null) {
						workspace.getCollections().add(collection);
					}
				}
			}
		}
		return workspace;
	}

	private RequestCollection readCollection(Path dir, boolean nativeBru) throws IOException {
		if (nativeBru && BruCollectionLoader.hasBruTree(dir)) {
			RequestCollection fromBru = BruCollectionLoader.load(dir, dir.getFileName().toString());
			Path meta = dir.resolve("collection.json");
			if (Files.exists(meta)) {
				RequestCollection jsonMeta = mapper.readValue(meta.toFile(), RequestCollection.class);
				fromBru.setId(jsonMeta.getId());
				if (fromBru.getName() == null || fromBru.getName().isBlank()) {
					fromBru.setName(jsonMeta.getName());
				}
				if (fromBru.getOpenApiSpec() == null || fromBru.getOpenApiSpec().isBlank()) {
					fromBru.setOpenApiSpec(jsonMeta.getOpenApiSpec());
				}
			}
			return fromBru;
		}
		Path meta = dir.resolve("collection.json");
		if (!Files.exists(meta)) {
			if (BruCollectionLoader.hasBruTree(dir)) {
				return BruCollectionLoader.load(dir, dir.getFileName().toString());
			}
			return null;
		}
		RequestCollection collection = mapper.readValue(meta.toFile(), RequestCollection.class);
		collection.setRequests(new ArrayList<>());
		Path requests = dir.resolve("requests");
		if (Files.isDirectory(requests)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(requests, "*.json")) {
				for (Path file : files) {
					collection.getRequests().add(mapper.readValue(file.toFile(), ApiRequest.class));
				}
			}
		}
		collection.getRequests().sort(Comparator.comparingInt(ApiRequest::getPosition));
		return collection;
	}

	private void preferInheritOnSamples(Workspace workspace) {
		if (workspace == null) {
			return;
		}
		for (RequestCollection collection : workspace.getCollections()) {
			for (ApiRequest request : collection.getRequests()) {
				if (("req-get-todo".equals(request.getId()) || "req-create-todo".equals(request.getId()))
						&& (request.getAuthType() == null || request.getAuthType().isBlank() || "none".equals(request.getAuthType()))) {
					request.setAuthType("inherit");
				}
			}
		}
	}

	private boolean hasCollectionFiles() throws IOException {
		if (!Files.isDirectory(collectionsDir)) {
			return false;
		}
		try (DirectoryStream<Path> dirs = Files.newDirectoryStream(collectionsDir)) {
			return dirs.iterator().hasNext();
		}
	}

	private long newestStamp() throws IOException {
		long newest = 0;
		if (Files.exists(indexFile)) {
			newest = Files.getLastModifiedTime(indexFile).toMillis();
		}
		if (!Files.isDirectory(collectionsDir)) {
			return newest;
		}
		try (var walk = Files.walk(collectionsDir)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				newest = Math.max(newest, Files.getLastModifiedTime(file).toMillis());
			}
		}
		return newest;
	}

	private void deleteMissing(Path dir, Set<String> keepIds, boolean requestFiles) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
			for (Path child : children) {
				String name = child.getFileName().toString();
				if (requestFiles) {
					if (name.endsWith(".json") && !keepIds.contains(name)) {
						Files.deleteIfExists(child);
					}
				}
				else if (Files.isDirectory(child) && !keepIds.contains(name) && !"requests".equals(name)) {
					deleteRecursively(child);
				}
			}
		}
	}

	private void deleteRecursively(Path dir) throws IOException {
		try (var walk = Files.walk(dir)) {
			for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(file);
			}
		}
	}

	private void writeJson(Path file, Object value) throws IOException {
		Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
		mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (java.nio.file.AtomicMoveNotSupportedException ex) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private Workspace deepCopy(Workspace workspace) {
		try {
			return mapper.readValue(mapper.writeValueAsString(workspace), Workspace.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not copy workspace", ex);
		}
	}

	public static class WorkspaceIndex {

		private List<String> collectionIds = new ArrayList<>();
		private List<com.apiflow.model.Environment> environments = new ArrayList<>();
		private List<com.apiflow.model.StoredCookie> cookies = new ArrayList<>();
		private List<com.apiflow.model.HistoryEntry> history = new ArrayList<>();
		private List<com.apiflow.model.KeyValue> variables = new ArrayList<>();
		private com.apiflow.model.WorkspaceSettings settings = new com.apiflow.model.WorkspaceSettings();

		public List<String> getCollectionIds() {
			if (collectionIds == null) {
				collectionIds = new ArrayList<>();
			}
			return collectionIds;
		}

		public void setCollectionIds(List<String> collectionIds) {
			this.collectionIds = collectionIds;
		}

		public List<com.apiflow.model.Environment> getEnvironments() {
			return environments;
		}

		public void setEnvironments(List<com.apiflow.model.Environment> environments) {
			this.environments = environments;
		}

		public List<com.apiflow.model.StoredCookie> getCookies() {
			return cookies;
		}

		public void setCookies(List<com.apiflow.model.StoredCookie> cookies) {
			this.cookies = cookies;
		}

		public List<com.apiflow.model.HistoryEntry> getHistory() {
			return history;
		}

		public void setHistory(List<com.apiflow.model.HistoryEntry> history) {
			this.history = history;
		}

		public List<com.apiflow.model.KeyValue> getVariables() {
			if (variables == null) {
				variables = new ArrayList<>();
			}
			return variables;
		}

		public void setVariables(List<com.apiflow.model.KeyValue> variables) {
			this.variables = variables;
		}

		public com.apiflow.model.WorkspaceSettings getSettings() {
			if (settings == null) {
				settings = new com.apiflow.model.WorkspaceSettings();
			}
			return settings;
		}

		public void setSettings(com.apiflow.model.WorkspaceSettings settings) {
			this.settings = settings;
		}

	}

}
