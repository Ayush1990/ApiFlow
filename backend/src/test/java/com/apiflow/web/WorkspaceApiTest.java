package com.apiflow.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceApiTest {

	@DynamicPropertySource
	static void dataFile(DynamicPropertyRegistry registry) throws Exception {
		Path dir = Files.createTempDirectory("apiflow-test");
		registry.add("apiflow.data-file", () -> dir.resolve("store.json").toString());
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void workspaceExecuteAndCollections() throws Exception {
		mockMvc.perform(get("/api/workspace"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.collections[0].name").value("JSONPlaceholder"))
			.andExpect(jsonPath("$.environments[0].name").value("Dev"));

		mockMvc.perform(post("/api/collections").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Billing\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.focusId").isNotEmpty())
			.andExpect(jsonPath("$.workspace.collections[?(@.name == 'Billing')]").exists());

		mockMvc.perform(post("/api/execute")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"GET\",\"url\":\"file:///etc/passwd\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ok").value(false));
	}

}
