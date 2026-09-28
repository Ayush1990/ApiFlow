package com.apiflow.model;

public class ActivityEvent {

	private String id;
	private String kind = "update";
	private String message = "";
	private long at;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getKind() {
		return kind == null ? "update" : kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public String getMessage() {
		return message == null ? "" : message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public long getAt() {
		return at;
	}

	public void setAt(long at) {
		this.at = at;
	}

}
