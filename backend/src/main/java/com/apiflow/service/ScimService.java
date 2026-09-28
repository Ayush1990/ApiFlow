package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.apiflow.store.FileStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ScimService {

	private final FileStore store;
	private final ObjectMapper mapper = new ObjectMapper();

	public ScimService(FileStore store) {
		this.store = store;
	}

	public String listUsers() {
		ObjectNode root = mapper.createObjectNode();
		root.putArray("schemas").add("urn:ietf:params:scim:api:messages:2.0:ListResponse");
		ArrayNode resources = root.putArray("Resources");
		for (ScimUser user : users()) {
			resources.add(userNode(user));
		}
		root.put("totalResults", resources.size());
		root.put("itemsPerPage", resources.size());
		root.put("startIndex", 1);
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not serialize SCIM users");
		}
	}

	public String createUser(String body) {
		try {
			JsonNode input = mapper.readTree(body);
			ScimUser user = new ScimUser(
				UUID.randomUUID().toString(),
				text(input.path("userName")),
				text(input.path("displayName")),
				text(input.path("emails").path(0).path("value")),
				true
			);
			save(user);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(userNode(user));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid SCIM user payload");
		}
	}

	public String patchUser(String id, String body) {
		ScimUser user = find(id);
		try {
			JsonNode input = mapper.readTree(body);
			for (JsonNode op : input.path("Operations")) {
				if ("replace".equalsIgnoreCase(op.path("op").asText(""))) {
					String path = op.path("path").asText("");
					String value = op.path("value").asText("");
					if ("displayName".equals(path)) {
						user = new ScimUser(user.id(), user.userName(), value, user.email(), user.active());
					}
					if ("active".equals(path)) {
						user = new ScimUser(user.id(), user.userName(), user.displayName(), user.email(), Boolean.parseBoolean(value));
					}
				}
			}
			save(user);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(userNode(user));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid SCIM patch");
		}
	}

	public void deleteUser(String id) {
		try {
			Files.deleteIfExists(usersDir().resolve(id + ".json"));
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not delete SCIM user");
		}
	}

	public String listGroups() {
		ObjectNode root = mapper.createObjectNode();
		root.putArray("schemas").add("urn:ietf:params:scim:api:messages:2.0:ListResponse");
		root.putArray("Resources");
		root.put("totalResults", 0);
		root.put("itemsPerPage", 0);
		root.put("startIndex", 1);
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not serialize SCIM groups");
		}
	}

	private ObjectNode userNode(ScimUser user) {
		ObjectNode node = mapper.createObjectNode();
		node.putArray("schemas").add("urn:ietf:params:scim:schemas:core:2.0:User");
		node.put("id", user.id());
		node.put("userName", user.userName());
		node.put("displayName", user.displayName());
		node.put("active", user.active());
		ObjectNode email = node.putArray("emails").addObject();
		email.put("value", user.email());
		email.put("primary", true);
		return node;
	}

	private List<ScimUser> users() {
		List<ScimUser> users = new ArrayList<>();
		Path dir = usersDir();
		if (!Files.isDirectory(dir)) {
			return users;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
			for (Path file : files) {
				users.add(mapper.readValue(file.toFile(), ScimUser.class));
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not read SCIM users");
		}
		return users;
	}

	private ScimUser find(String id) {
		Path file = usersDir().resolve(id + ".json");
		if (!Files.exists(file)) {
			throw new IllegalArgumentException("SCIM user not found");
		}
		try {
			return mapper.readValue(file.toFile(), ScimUser.class);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not read SCIM user");
		}
	}

	private void save(ScimUser user) {
		try {
			Files.createDirectories(usersDir());
			mapper.writerWithDefaultPrettyPrinter().writeValue(usersDir().resolve(user.id() + ".json").toFile(), user);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not save SCIM user");
		}
	}

	private Path usersDir() {
		return store.root().resolve("scim").resolve("users");
	}

	private static String text(JsonNode node) {
		return node == null || node.isMissingNode() ? "" : node.asText("").trim();
	}

	public record ScimUser(String id, String userName, String displayName, String email, boolean active) {
	}

}
