package com.apiflow.model;

public class ResponseExample {

	private String name = "Example";
	private int status = 200;
	private String contentType = "application/json";
	private String body = "";
	private String requestHeaders = "";
	private String requestBody = "";

	public String getName() {
		return name == null || name.isBlank() ? "Example" : name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getStatus() {
		return status <= 0 ? 200 : status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getContentType() {
		return contentType == null || contentType.isBlank() ? "application/json" : contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getBody() {
		return body == null ? "" : body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getRequestHeaders() {
		return requestHeaders == null ? "" : requestHeaders;
	}

	public void setRequestHeaders(String requestHeaders) {
		this.requestHeaders = requestHeaders;
	}

	public String getRequestBody() {
		return requestBody == null ? "" : requestBody;
	}

	public void setRequestBody(String requestBody) {
		this.requestBody = requestBody;
	}

}
