package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.apiflow.model.RequestCollection;

class BruCollectionLoaderTest {

	@TempDir
	Path temp;

	@Test
	void loadsCollectionFromBruTree() throws Exception {
		Path collectionDir = temp.resolve("demo");
		Files.createDirectories(collectionDir);
		Files.writeString(collectionDir.resolve("collection.bru"), """
				meta {
				  name: Demo Collection
				}
				""");
		Files.writeString(collectionDir.resolve("hello.bru"), ExportService.BrunoExporter.request(sampleRequest(), ""));

		RequestCollection collection = BruCollectionLoader.load(collectionDir, "demo-id");
		assertEquals("Demo Collection", collection.getName());
		assertEquals(1, collection.getRequests().size());
		assertEquals("Hello", collection.getRequests().get(0).getName());
		assertTrue(BruCollectionLoader.hasBruTree(collectionDir));
	}

	private static com.apiflow.model.ApiRequest sampleRequest() {
		com.apiflow.model.ApiRequest request = new com.apiflow.model.ApiRequest();
		request.setName("Hello");
		request.setMethod("GET");
		request.setUrl("https://example.com");
		return request;
	}

}
