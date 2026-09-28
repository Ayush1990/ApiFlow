package com.apiflow.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.apiflow.model.RequestCollection;

public final class BruDiskSync {

	private BruDiskSync() {
	}

	public static void syncCollection(Path collectionDir, RequestCollection collection) throws IOException {
		BruFolderExporter.syncToDisk(collectionDir, collection);
		cleanupLegacyFlatDir(collectionDir);
	}

	private static void cleanupLegacyFlatDir(Path collectionDir) throws IOException {
		Path legacy = collectionDir.resolve("bru");
		if (!Files.isDirectory(legacy)) {
			return;
		}
		try (var stream = Files.list(legacy)) {
			for (Path file : stream.toList()) {
				Files.deleteIfExists(file);
			}
		}
		Files.deleteIfExists(legacy);
	}

}
