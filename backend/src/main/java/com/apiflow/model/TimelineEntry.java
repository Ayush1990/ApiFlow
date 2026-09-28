package com.apiflow.model;

public class TimelineEntry {

	private String kind = "";
	private String label = "";
	private long atMs;
	private String detail = "";

	public TimelineEntry() {
	}

	public TimelineEntry(String kind, String label, long atMs, String detail) {
		this.kind = kind;
		this.label = label;
		this.atMs = atMs;
		this.detail = detail;
	}

	public String getKind() {
		return kind == null ? "" : kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public String getLabel() {
		return label == null ? "" : label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public long getAtMs() {
		return atMs;
	}

	public void setAtMs(long atMs) {
		this.atMs = atMs;
	}

	public String getDetail() {
		return detail == null ? "" : detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}

}
