package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.apiflow.store.FileStore;

class GitServiceTest {

	@TempDir
	Path temp;

	private FileStore store() throws Exception {
		Path data = temp.resolve("data/apiflow.json");
		Files.createDirectories(data.getParent());
		Files.writeString(data, "{\"collections\":[],\"environments\":[],\"settings\":{}}", StandardCharsets.UTF_8);
		return new FileStore(data.toString());
	}

	@Test
	void resolvesConflictBlocks() throws Exception {
		FileStore store = store();
		GitService git = new GitService(store);
		Path file = store.root().resolve("conflict.txt");
		Files.writeString(file, "before\n<<<<<<<\nours\n=======\ntheirs\n>>>>>>> branch\nafter\n");
		git.resolveConflict("conflict.txt", 0, "ours");
		String resolved = Files.readString(file);
		assertTrue(resolved.contains("ours"));
		assertEquals(false, resolved.contains("<<<<<<<"));
	}

	@Test
	void stagesResolvedFileAfterConflict() throws Exception {
		FileStore store = store();
		GitService git = new GitService(store);
		Path root = store.root();
		Files.writeString(root.resolve("conflict.txt"), "before\n<<<<<<<\nours\n=======\ntheirs\n>>>>>>> branch\nafter\n");
		Process init = new ProcessBuilder("git", "-c", "init.templateDir=", "init")
			.directory(root.toFile())
			.redirectErrorStream(true)
			.start();
		Assumptions.assumeTrue(init.waitFor() == 0, "git init unavailable in this environment");
		git.resolveConflict("conflict.txt", 0, "ours");
		String staged = new String(new ProcessBuilder("git", "diff", "--cached", "--name-only")
			.directory(root.toFile())
			.redirectErrorStream(true)
			.start()
			.getInputStream()
			.readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(staged.contains("conflict.txt"));
	}

}
