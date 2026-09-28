package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.apiflow.store.FileStore;

@Service
public class GitService {

	private final FileStore store;

	public GitService(FileStore store) {
		this.store = store;
	}

	public GitStatus status() {
		return view(root(), "");
	}

	public GitStatus init() {
		Path root = root();
		if (!Files.isDirectory(root.resolve(".git"))) {
			run(root, "init");
		}
		return view(root, "");
	}

	public GitStatus commit(String message) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("Commit message is required");
		}
		Path root = requireRepo();
		run(root, "add", "-A");
		String output = run(root, "commit", "-m", message.trim());
		return view(root, output);
	}

	public GitStatus pull(String remote) {
		Path root = requireRepo();
		String output;
		if (remote == null || remote.isBlank()) {
			output = run(root, "pull");
		}
		else {
			String branch = safe(root, "branch", "--show-current");
			output = branch.isBlank() ? run(root, "pull", remote.trim()) : run(root, "pull", remote.trim(), branch);
		}
		return view(root, output);
	}

	public GitStatus push(String remote) {
		Path root = requireRepo();
		String output = remote == null || remote.isBlank() ? run(root, "push") : run(root, "push", remote.trim(), "HEAD");
		return view(root, output);
	}

	public GitStatus fetch(String remote) {
		Path root = requireRepo();
		String output = remote == null || remote.isBlank() ? run(root, "fetch", "--all", "--prune") : run(root, "fetch", remote.trim(), "--prune");
		return view(root, output);
	}

	public GitStatus remote(String name, String url) {
		if (name == null || name.isBlank() || url == null || url.isBlank()) {
			throw new IllegalArgumentException("Remote name and URL are required");
		}
		Path root = requireRepo();
		boolean exists = split(safe(root, "remote")).contains(name.trim());
		if (exists) {
			run(root, "remote", "set-url", name.trim(), url.trim());
		}
		else {
			run(root, "remote", "add", name.trim(), url.trim());
		}
		return view(root, "");
	}

	public GitStatus resolveConflict(String relativePath, int blockIndex, String choice) {
		if (relativePath == null || relativePath.isBlank()) {
			throw new IllegalArgumentException("File path is required");
		}
		if (choice == null || choice.isBlank()) {
			throw new IllegalArgumentException("Resolution choice is required (ours, theirs, both)");
		}
		Path root = root();
		Path file = root.resolve(relativePath.trim()).normalize();
		if (!file.startsWith(root) || !Files.isRegularFile(file)) {
			throw new IllegalArgumentException("Conflict file not found");
		}
		try {
			String content = Files.readString(file, StandardCharsets.UTF_8);
			List<ConflictBlock> blocks = parseConflict(content);
			if (blockIndex < 0 || blockIndex >= blocks.size()) {
				throw new IllegalArgumentException("Conflict block index out of range");
			}
			ConflictBlock block = blocks.get(blockIndex);
			String replacement = switch (choice.trim().toLowerCase()) {
				case "ours" -> block.ours();
				case "theirs" -> block.theirs();
				case "both" -> block.ours() + "\n" + block.theirs();
				default -> throw new IllegalArgumentException("Choice must be ours, theirs, or both");
			};
			String resolved = replaceConflictBlock(content, blockIndex, replacement);
			Files.writeString(file, resolved, StandardCharsets.UTF_8);
			String staged = "";
			if (Files.isDirectory(root.resolve(".git"))) {
				staged = run(root, "add", relativePath.trim());
			}
			String message = "Resolved conflict in " + relativePath;
			if (!staged.isBlank()) {
				message += "\n" + staged;
			}
			return view(root, message);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not resolve conflict");
		}
	}

	public List<ConflictFile> conflicts() {
		Path root = root();
		List<ConflictFile> files = new ArrayList<>();
		if (!Files.isDirectory(root)) {
			return files;
		}
		try (var walk = Files.walk(root)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				if (file.toString().contains("/.git/")) {
					continue;
				}
				String content = Files.readString(file, StandardCharsets.UTF_8);
				if (content.contains("<<<<<<<")) {
					files.add(new ConflictFile(root.relativize(file).toString(), parseConflict(content)));
				}
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not scan for merge conflicts");
		}
		return files;
	}

	private List<ConflictBlock> parseConflict(String content) {
		List<ConflictBlock> blocks = new ArrayList<>();
		String[] lines = content.split("\\R", -1);
		int index = 0;
		while (index < lines.length) {
			if (!"<<<<<<<".equals(lines[index].trim())) {
				index++;
				continue;
			}
			index++;
			StringBuilder ours = new StringBuilder();
			while (index < lines.length && !"=======".equals(lines[index].trim())) {
				ours.append(lines[index]).append('\n');
				index++;
			}
			index++;
			StringBuilder theirs = new StringBuilder();
			while (index < lines.length && !lines[index].startsWith(">>>>>>>")) {
				theirs.append(lines[index]).append('\n');
				index++;
			}
			blocks.add(new ConflictBlock(ours.toString().trim(), theirs.toString().trim()));
			index++;
		}
		return blocks;
	}

	public GitStatus clone(String url, String directory) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Clone URL is required");
		}
		Path target = root().resolve(directory == null || directory.isBlank() ? "clone" : directory.trim());
		if (Files.exists(target)) {
			throw new IllegalArgumentException("Target folder already exists");
		}
		run(root(), "clone", url.trim(), target.toString());
		return view(target, "Cloned into " + target);
	}

	public GitStatus checkout(String name, boolean create) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Branch name is required");
		}
		Path root = requireRepo();
		if (create) {
			run(root, "checkout", "-b", name.trim());
		}
		else {
			run(root, "checkout", name.trim());
		}
		return view(root, "");
	}

	private GitStatus view(Path root, String output) {
		if (!Files.isDirectory(root.resolve(".git"))) {
			return new GitStatus(false, root.toString(), output == null ? "" : output, "This data folder is not a git repository yet.", "", List.of(), List.of(), "");
		}
		String branch = safe(root, "branch", "--show-current");
		List<String> branches = split(safe(root, "branch", "--format=%(refname:short)"));
		List<String> remotes = split(safe(root, "remote"));
		String diff = trim(join(safe(root, "diff"), safe(root, "diff", "--cached")));
		String status = safe(root, "status", "--short", "--branch");
		String shown = output == null || output.isBlank() ? status : output + (status.isBlank() ? "" : "\n" + status);
		return new GitStatus(true, root.toString(), shown, "", branch, branches, remotes, diff);
	}

	private Path requireRepo() {
		Path root = root();
		if (!Files.isDirectory(root.resolve(".git"))) {
			throw new IllegalArgumentException("Initialize the data folder before committing");
		}
		return root;
	}

	private Path root() {
		Path path = store.root().toAbsolutePath().normalize();
		String collectionId = store.copy().getSettings().getGitCollectionId();
		if (collectionId != null && !collectionId.isBlank()) {
			path = path.resolve("collections").resolve(collectionId.trim());
		}
		try {
			Files.createDirectories(path);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not open " + path);
		}
		return path;
	}

	private String replaceConflictBlock(String content, int targetIndex, String replacement) {
		String[] lines = content.split("\\R", -1);
		StringBuilder out = new StringBuilder();
		int index = 0;
		int blockIndex = 0;
		while (index < lines.length) {
			if (!"<<<<<<<".equals(lines[index].trim())) {
				out.append(lines[index]).append('\n');
				index++;
				continue;
			}
			if (blockIndex == targetIndex) {
				out.append(replacement).append('\n');
				index++;
				while (index < lines.length && !"=======".equals(lines[index].trim())) {
					index++;
				}
				index++;
				while (index < lines.length && !lines[index].startsWith(">>>>>>>")) {
					index++;
				}
				index++;
				blockIndex++;
				continue;
			}
			out.append(lines[index]).append('\n');
			index++;
			while (index < lines.length && !"=======".equals(lines[index].trim())) {
				out.append(lines[index]).append('\n');
				index++;
			}
			out.append(lines[index]).append('\n');
			index++;
			while (index < lines.length && !lines[index].startsWith(">>>>>>>")) {
				out.append(lines[index]).append('\n');
				index++;
			}
			if (index < lines.length) {
				out.append(lines[index]).append('\n');
				index++;
			}
			blockIndex++;
		}
		return out.toString();
	}

	private String run(Path directory, String... args) {
		return execute(directory, args);
	}

	private String safe(Path directory, String... args) {
		try {
			return execute(directory, args);
		}
		catch (IllegalArgumentException ex) {
			return "";
		}
	}

	private String execute(Path directory, String... args) {
		String[] command = new String[args.length + 1];
		command[0] = "git";
		System.arraycopy(args, 0, command, 1, args.length);
		try {
			Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
			boolean finished = process.waitFor(30, TimeUnit.SECONDS);
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (!finished) {
				process.destroyForcibly();
				throw new IllegalArgumentException("git timed out");
			}
			if (process.exitValue() != 0) {
				throw new IllegalArgumentException(output.isBlank() ? "git " + args[0] + " failed" : output);
			}
			return output;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("git is not available: " + ex.getMessage());
		}
	}

	private static List<String> split(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		return Arrays.stream(text.split("\\R")).map(String::trim).filter(line -> !line.isEmpty()).toList();
	}

	private static String join(String first, String second) {
		if (first == null || first.isBlank()) {
			return second == null ? "" : second;
		}
		if (second == null || second.isBlank()) {
			return first;
		}
		return first + "\n" + second;
	}

	private static String trim(String text) {
		if (text == null) {
			return "";
		}
		return text.length() > 12000 ? text.substring(0, 12000) + "\n…" : text;
	}

	public record GitStatus(boolean repo, String path, String output, String error, String branch, List<String> branches, List<String> remotes, String diff) {
	}

	public record ConflictBlock(String ours, String theirs) {
	}

	public record ConflictFile(String path, List<ConflictBlock> blocks) {
	}

}
