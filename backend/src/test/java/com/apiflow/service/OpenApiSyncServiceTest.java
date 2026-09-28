package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.apiflow.model.RequestCollection;

class OpenApiSyncServiceTest {

	private final OpenApiSyncService service = new OpenApiSyncService();

	@Test
	void syncUpdatesExistingOperations() {
		RequestCollection collection = new RequestCollection();
		collection.setName("API");
		String spec = """
				{
				  "openapi": "3.0.0",
				  "info": { "title": "Demo", "version": "1.0.0" },
				  "paths": {
				    "/pets": {
				      "get": { "summary": "List pets" },
				      "post": { "summary": "Create pet" }
				    }
				  }
				}
				""";
		service.syncFromSpec(collection, spec, "additive", false);
		assertEquals(2, collection.getRequests().size());
		service.syncFromSpec(collection, spec, "update", false);
		assertEquals(2, collection.getRequests().size());
		assertTrue(collection.getRequests().stream().anyMatch(request -> "List pets".equals(request.getName())));
	}

	@Test
	void diffReportsAddedAndRemoved() {
		RequestCollection collection = new RequestCollection();
		service.syncFromSpec(collection, """
				{"openapi":"3.0.0","paths":{"/pets":{"get":{"summary":"List pets"}}}}
				""", "additive", false);
		OpenApiSyncService.OpenApiDiff diff = service.diff(collection, """
				{"openapi":"3.0.0","paths":{"/pets":{"get":{"summary":"List pets"}},"/orders":{"post":{"summary":"Create order"}}}}
				""");
		assertEquals(1, diff.added().size());
		assertTrue(diff.added().get(0).contains("POST"));
	}

}
