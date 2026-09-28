package com.apiflow.model;

public class ApiSpec {

	private String id;
	private String name;
	private String format = "openapi";
	private String content = "";
	private java.util.List<SpecFile> files = new java.util.ArrayList<>();
	private String collectionId = "";
	private long updatedAt;

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

	public String getFormat() {
		return format == null ? "openapi" : format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public String getContent() {
		return content == null ? "" : content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public java.util.List<SpecFile> getFiles() {
		if (files == null) {
			files = new java.util.ArrayList<>();
		}
		return files;
	}

	public void setFiles(java.util.List<SpecFile> files) {
		this.files = files;
	}

	public String getCollectionId() {
		return collectionId == null ? "" : collectionId;
	}

	public void setCollectionId(String collectionId) {
		this.collectionId = collectionId;
	}

	public long getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(long updatedAt) {
		this.updatedAt = updatedAt;
	}

	public static class SpecFile {
		private String path = "openapi.yaml";
		private String content = "";

		public String getPath() {
			return path == null ? "" : path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public String getContent() {
			return content == null ? "" : content;
		}

		public void setContent(String content) {
			this.content = content;
		}
	}

}
