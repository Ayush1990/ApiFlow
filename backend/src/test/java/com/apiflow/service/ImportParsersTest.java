package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.RequestCollection;

class ImportParsersTest {

	private ImportService imports() {
		return new ImportService(new OpenCollectionService());
	}

	@Test
	void parsesCurl() {
		RequestCollection collection = CurlParser.collection("curl -X GET 'https://example.com/users' -H 'Accept: application/json' -H 'Authorization: Bearer secret-token'");
		ApiRequest request = collection.getRequests().get(0);
		assertEquals("GET", request.getMethod());
		assertEquals("https://example.com/users", request.getUrl());
		assertEquals("bearer", request.getAuthType());
		assertEquals("secret-token", request.getAuthToken());
		assertEquals("Accept", request.getHeaders().get(0).getKey());
	}

	@Test
	void parsesBrunoAndInsomnia() {
		String bruno = """
				meta {
				  name: List users
				  type: http
				}

				get {
				  url: {{baseUrl}}/users
				  body: json
				  auth: bearer
				}

				auth:bearer {
				  token: {{token}}
				}
				""";
		RequestCollection brunoCollection = imports().importDocument(bruno);
		assertEquals("List users", brunoCollection.getRequests().get(0).getName());
		assertEquals("{{baseUrl}}/users", brunoCollection.getRequests().get(0).getUrl());
		assertEquals("bearer", brunoCollection.getRequests().get(0).getAuthType());
		assertEquals("{{token}}", brunoCollection.getRequests().get(0).getAuthToken());

		String insomnia = """
				{
				  "_type": "export",
				  "resources": [
				    { "_type": "workspace", "_id": "wrk_1", "name": "Billing" },
				    { "_type": "request", "_id": "req_1", "parentId": "wrk_1", "name": "Get invoice", "method": "GET", "url": "https://example.com/invoices" }
				  ]
				}
				""";
		RequestCollection insomniaCollection = imports().importDocument(insomnia);
		assertEquals("Billing", insomniaCollection.getName());
		assertEquals("Get invoice", insomniaCollection.getRequests().get(0).getName());
		assertTrue(insomniaCollection.getRequests().get(0).getUrl().contains("invoices"));
	}

}
