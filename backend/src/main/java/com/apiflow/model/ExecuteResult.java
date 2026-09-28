package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class ExecuteResult {

	private boolean ok;
	private int status;
	private String statusText = "";
	private List<KeyValue> headers = new ArrayList<>();
	private String body = "";
	private long timeMs;
	private int size;
	private String error;
	private String contentType = "";
	private boolean binary;
	private String bodyBase64 = "";
	private List<CheckResult> checks = new ArrayList<>();
	private List<KeyValue> cookies = new ArrayList<>();
	private List<TimelineEntry> timeline = new ArrayList<>();
	private List<String> scriptLogs = new ArrayList<>();
	private String visualizerTemplate = "";
	private String visualizerData = "{}";

	public static ExecuteResult failure(String error) {
		ExecuteResult result = new ExecuteResult();
		result.ok = false;
		result.error = error;
		return result;
	}

	public boolean isOk() {
		return ok;
	}

	public void setOk(boolean ok) {
		this.ok = ok;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getStatusText() {
		return statusText;
	}

	public void setStatusText(String statusText) {
		this.statusText = statusText;
	}

	public List<KeyValue> getHeaders() {
		return headers;
	}

	public void setHeaders(List<KeyValue> headers) {
		this.headers = headers;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public long getTimeMs() {
		return timeMs;
	}

	public void setTimeMs(long timeMs) {
		this.timeMs = timeMs;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public boolean isBinary() {
		return binary;
	}

	public void setBinary(boolean binary) {
		this.binary = binary;
	}

	public String getBodyBase64() {
		return bodyBase64;
	}

	public void setBodyBase64(String bodyBase64) {
		this.bodyBase64 = bodyBase64;
	}

	public List<CheckResult> getChecks() {
		if (checks == null) {
			checks = new ArrayList<>();
		}
		return checks;
	}

	public void setChecks(List<CheckResult> checks) {
		this.checks = checks;
	}

	public List<KeyValue> getCookies() {
		if (cookies == null) {
			cookies = new ArrayList<>();
		}
		return cookies;
	}

	public void setCookies(List<KeyValue> cookies) {
		this.cookies = cookies;
	}

	public List<TimelineEntry> getTimeline() {
		if (timeline == null) {
			timeline = new ArrayList<>();
		}
		return timeline;
	}

	public void setTimeline(List<TimelineEntry> timeline) {
		this.timeline = timeline;
	}

	public List<String> getScriptLogs() {
		if (scriptLogs == null) {
			scriptLogs = new ArrayList<>();
		}
		return scriptLogs;
	}

	public void setScriptLogs(List<String> scriptLogs) {
		this.scriptLogs = scriptLogs;
	}

	public String getVisualizerTemplate() {
		return visualizerTemplate == null ? "" : visualizerTemplate;
	}

	public void setVisualizerTemplate(String visualizerTemplate) {
		this.visualizerTemplate = visualizerTemplate;
	}

	public String getVisualizerData() {
		return visualizerData == null ? "{}" : visualizerData;
	}

	public void setVisualizerData(String visualizerData) {
		this.visualizerData = visualizerData;
	}

}
