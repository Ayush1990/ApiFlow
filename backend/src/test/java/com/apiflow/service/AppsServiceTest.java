package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.apiflow.store.FileStore;

class AppsServiceTest {

	@TempDir
	Path temp;

	@Test
	void savesAndListsApps() throws Exception {
		Path data = temp.resolve("data/apiflow.json");
		Files.createDirectories(data.getParent());
		Files.writeString(data, "{\"collections\":[],\"environments\":[],\"settings\":{}}");
		FileStore store = new FileStore(data.toString());
		AppsService apps = new AppsService(store, null, null);
		AppsService.AppManifest manifest = new AppsService.AppManifest();
		manifest.setName("Smoke app");
		manifest.setTriggerType("script");
		manifest.setScript("setVar('ok', '1')");
		AppsService.AppManifest saved = apps.save(manifest);
		assertFalse(saved.getId().isBlank());
		assertEquals(1, apps.list().size());
	}

}
