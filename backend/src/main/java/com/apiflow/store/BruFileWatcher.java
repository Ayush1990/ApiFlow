package com.apiflow.store;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class BruFileWatcher {

	private final FileStore store;
	private final Path collectionsDir;
	private final WatchService watchService;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "bru-file-watcher");
		thread.setDaemon(true);
		return thread;
	});
	private volatile boolean running = true;
	private volatile long debounceUntil;

	public BruFileWatcher(FileStore store) throws IOException {
		this.store = store;
		this.collectionsDir = store.root().resolve("collections");
		this.watchService = FileSystems.getDefault().newWatchService();
		registerTree(collectionsDir);
		scheduler.execute(this::pollLoop);
	}

	private void registerTree(Path root) throws IOException {
		if (!Files.isDirectory(root)) {
			Files.createDirectories(root);
		}
		try (var walk = Files.walk(root)) {
			for (Path dir : walk.filter(Files::isDirectory).toList()) {
				dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
			}
		}
	}

	private void pollLoop() {
		while (running) {
			WatchKey key;
			try {
				key = watchService.poll(2, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
			if (key == null) {
				continue;
			}
			boolean bruChanged = false;
			for (WatchEvent<?> event : key.pollEvents()) {
				Path relative = (Path) event.context();
				if (relative != null && relative.toString().endsWith(".bru")) {
					bruChanged = true;
				}
				if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
					Path created = ((Path) key.watchable()).resolve(relative);
					if (Files.isDirectory(created)) {
						try {
							created.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
						}
						catch (IOException ignored) {
						}
					}
				}
			}
			key.reset();
			if (bruChanged) {
				scheduleReload();
			}
		}
	}

	private void scheduleReload() {
		debounceUntil = System.currentTimeMillis() + 400;
		scheduler.schedule(() -> {
			if (System.currentTimeMillis() >= debounceUntil) {
				store.reloadFromDiskIfExternal();
			}
		}, 450, TimeUnit.MILLISECONDS);
	}

	@PreDestroy
	public void shutdown() {
		running = false;
		scheduler.shutdownNow();
		try {
			watchService.close();
		}
		catch (IOException ignored) {
		}
	}

}
