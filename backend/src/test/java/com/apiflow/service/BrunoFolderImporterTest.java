package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BrunoFolderImporterTest {

	@Test
	void importsFolderOfBruFiles() {
		var collection = BrunoFolderImporter.importFiles(List.of(
			new BrunoFolderImporter.BrunoFile("Shop/bruno.json", "{\"name\":\"Shop\"}"),
			new BrunoFolderImporter.BrunoFile("Shop/users/folder.bru", "meta {\n  name: Users\n  type: folder\n}\n"),
			new BrunoFolderImporter.BrunoFile("Shop/users/list.bru", """
				meta {
				  name: List users
				  type: http
				}
				get {
				  url: {{baseUrl}}/users
				  body: none
				  auth: none
				}
				""")
		));
		assertEquals("Shop", collection.getName());
		assertEquals("Users", collection.getFolders().get(0).getName());
		assertEquals("List users", collection.getRequests().get(0).getName());
		assertEquals(collection.getFolders().get(0).getId(), collection.getRequests().get(0).getFolderId());
		assertTrue(collection.getRequests().get(0).getUrl().contains("/users"));
		assertEquals("/todos/1", MockServer.pathOf("{{baseUrl}}/todos/1"));
		assertEquals("id=1", MockServer.queryOf("{{baseUrl}}/todos/1?id=1"));
		assertEquals(1, MockServer.matchScore("id=1", "id=1&extra=yes"));
		assertEquals(-1, MockServer.matchScore("id=2", "id=1"));
		var environments = BrunoFolderImporter.environments(List.of(
			new BrunoFolderImporter.BrunoFile("Shop/environments/Dev.bru", """
				vars {
				  baseUrl: https://example.test
				  token: abc
				}
				vars:secret [
				  token
				]
				""")
		));
		assertEquals("Dev", environments.get(0).getName());
		assertEquals("https://example.test", environments.get(0).getVariables().get(0).getValue());
		assertTrue(environments.get(0).getVariables().get(1).isSecret());
	}

}
