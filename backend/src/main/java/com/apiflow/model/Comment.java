package com.apiflow.model;

public class Comment {

	private String id;
	private String targetType = "run";
	private String targetId = "";
	private String author = "local";
	private String body = "";
	private long createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTargetType() {
		return targetType == null ? "run" : targetType;
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

	public String getAuthor() {
		return author == null ? "" : author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getBody() {
		return body == null ? "" : body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}

}
