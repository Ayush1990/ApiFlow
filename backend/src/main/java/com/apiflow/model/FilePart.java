package com.apiflow.model;

public class FilePart {

	private String key = "";
	private String fileName = "";
	private String contentType = "application/octet-stream";
	private String dataBase64 = "";
	private boolean enabled = true;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getDataBase64() {
		return dataBase64;
	}

	public void setDataBase64(String dataBase64) {
		this.dataBase64 = dataBase64;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
