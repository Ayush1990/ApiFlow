package com.apiflow.model;

public class CheckResult {

	private String type = "";
	private boolean passed;
	private String message = "";

	public CheckResult() {
	}

	public CheckResult(String type, boolean passed, String message) {
		this.type = type;
		this.passed = passed;
		this.message = message;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public boolean isPassed() {
		return passed;
	}

	public void setPassed(boolean passed) {
		this.passed = passed;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
