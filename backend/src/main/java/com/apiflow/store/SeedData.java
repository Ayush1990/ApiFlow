package com.apiflow.store;

import java.util.ArrayList;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.Environment;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.Workspace;

public final class SeedData {

	private SeedData() {
	}

	public static Workspace workspace() {
		Environment dev = new Environment();
		dev.setId("env-dev");
		dev.setName("Dev");
		dev.setVariables(new ArrayList<>());
		dev.getVariables().add(new KeyValue("baseUrl", "https://jsonplaceholder.typicode.com", true));

		ApiRequest getTodo = new ApiRequest();
		getTodo.setId("req-get-todo");
		getTodo.setName("Get todo");
		getTodo.setMethod("GET");
		getTodo.setUrl("{{baseUrl}}/todos/1");
		getTodo.getHeaders().add(new KeyValue("Accept", "application/json", true));
		getTodo.setAuthType("inherit");

		ApiRequest createTodo = new ApiRequest();
		createTodo.setId("req-create-todo");
		createTodo.setName("Create todo");
		createTodo.setMethod("POST");
		createTodo.setUrl("{{baseUrl}}/todos");
		createTodo.setBodyType("json");
		createTodo.setBody("""
				{
				  "title": "ApiFlow",
				  "completed": false,
				  "userId": 1
				}
				""");
		createTodo.getHeaders().add(new KeyValue("Accept", "application/json", true));
		createTodo.setAuthType("inherit");

		RequestCollection collection = new RequestCollection();
		collection.setId("col-sample");
		collection.setName("JSONPlaceholder");
		collection.getRequests().add(getTodo);
		collection.getRequests().add(createTodo);

		Workspace workspace = new Workspace();
		workspace.getCollections().add(collection);
		workspace.getEnvironments().add(dev);
		return workspace;
	}

}
