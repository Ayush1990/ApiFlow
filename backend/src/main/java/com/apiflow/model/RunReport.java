package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class RunReport {

	private String id = "";
	private String collectionId = "";
	private long startedAt;
	private String shareUrl = "";
	private String shareFile = "";
	private int passed;
	private int failed;
	private boolean stopped;
	private List<RunItem> items = new ArrayList<>();

	public String getId() {
		return id == null ? "" : id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCollectionId() {
		return collectionId == null ? "" : collectionId;
	}

	public void setCollectionId(String collectionId) {
		this.collectionId = collectionId;
	}

	public long getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(long startedAt) {
		this.startedAt = startedAt;
	}

	public String getShareUrl() {
		return shareUrl == null ? "" : shareUrl;
	}

	public void setShareUrl(String shareUrl) {
		this.shareUrl = shareUrl;
	}

	public String getShareFile() {
		return shareFile == null ? "" : shareFile;
	}

	public void setShareFile(String shareFile) {
		this.shareFile = shareFile;
	}

	public int getPassed() {
		return passed;
	}

	public void setPassed(int passed) {
		this.passed = passed;
	}

	public int getFailed() {
		return failed;
	}

	public void setFailed(int failed) {
		this.failed = failed;
	}

	public boolean isStopped() {
		return stopped;
	}

	public void setStopped(boolean stopped) {
		this.stopped = stopped;
	}

	public List<RunItem> getItems() {
		if (items == null) {
			items = new ArrayList<>();
		}
		return items;
	}

	public void setItems(List<RunItem> items) {
		this.items = items;
	}

}
