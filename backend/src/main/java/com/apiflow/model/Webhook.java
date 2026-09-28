package com.apiflow.model;

public class Webhook {

	private String id;
	private String name = "";
	private String targetType = "collection";
	private String targetId = "";
	private String environmentId = "";
	private boolean enabled = true;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name == null ? "" : name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTargetType() {
		return targetType == null ? "collection" : targetType;
	}

	public void setTargetType(String targetType) {
		this.targetType = targetType;
	}

	public String getTargetId() {
		return targetId == null ? "" : targetId;
	}

	public void setTargetId(String targetId) {
		this.targetId = targetId;
	}

	public String getEnvironmentId() {
		return environmentId == null ? "" : environmentId;
	}

	public void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
