package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class Environment {

	private String id;
	private String name;
	private boolean global = true;
	private String collectionId = "";
	private java.util.Map<String, String> externalSecrets = new java.util.LinkedHashMap<>();
	private List<KeyValue> variables = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isGlobal() {
		return global;
	}

	public void setGlobal(boolean global) {
		this.global = global;
	}

	public String getCollectionId() {
		return collectionId == null ? "" : collectionId;
	}

	public void setCollectionId(String collectionId) {
		this.collectionId = collectionId;
	}

	public java.util.Map<String, String> getExternalSecrets() {
		if (externalSecrets == null) {
			externalSecrets = new java.util.LinkedHashMap<>();
		}
		return externalSecrets;
	}

	public void setExternalSecrets(java.util.Map<String, String> externalSecrets) {
		this.externalSecrets = externalSecrets;
	}

	public List<KeyValue> getVariables() {
		if (variables == null) {
			variables = new ArrayList<>();
		}
		return variables;
	}

	public void setVariables(List<KeyValue> variables) {
		this.variables = variables;
	}

}
