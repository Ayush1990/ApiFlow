package com.apiflow.model;

public class CollectionPullRequest {

	private String id;
	private String title = "";
	private String sourceCollectionId = "";
	private String targetCollectionId = "";
	private String status = "open";
	private long createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title == null ? "" : title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSourceCollectionId() {
		return sourceCollectionId == null ? "" : sourceCollectionId;
	}

	public void setSourceCollectionId(String sourceCollectionId) {
		this.sourceCollectionId = sourceCollectionId;
	}

	public String getTargetCollectionId() {
		return targetCollectionId == null ? "" : targetCollectionId;
	}

	public void setTargetCollectionId(String targetCollectionId) {
		this.targetCollectionId = targetCollectionId;
	}

	public String getStatus() {
		return status == null ? "open" : status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}

}
