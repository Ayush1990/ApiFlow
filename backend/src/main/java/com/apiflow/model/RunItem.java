package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class RunItem {

	private String requestId = "";
	private String name = "";
	private String method = "";
	private int status;
	private boolean ok;
	private String error = "";
	private String responseBody = "";
	private List<CheckResult> checks = new ArrayList<>();

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public boolean isOk() {
		return ok;
	}

	public void setOk(boolean ok) {
		this.ok = ok;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getResponseBody() {
		return responseBody == null ? "" : responseBody;
	}

	public void setResponseBody(String responseBody) {
		this.responseBody = responseBody;
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

}
