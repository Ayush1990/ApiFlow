package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class InventoryApp {

	private String id;
	private String name = "";
	private String environment = "";
	private long capturedAt;
	private int matched;
	private int unmatched;
	private List<CapturedCall> calls = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name == null ? "" : name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEnvironment() {
		return environment == null ? "" : environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public long getCapturedAt() {
		return capturedAt;
	}

	public void setCapturedAt(long capturedAt) {
		this.capturedAt = capturedAt;
	}

	public int getMatched() {
		return matched;
	}

	public void setMatched(int matched) {
		this.matched = matched;
	}

	public int getUnmatched() {
		return unmatched;
	}

	public void setUnmatched(int unmatched) {
		this.unmatched = unmatched;
	}

	public List<CapturedCall> getCalls() {
		if (calls == null) {
			calls = new ArrayList<>();
		}
		return calls;
	}

	public void setCalls(List<CapturedCall> calls) {
		this.calls = calls;
	}

	public static class CapturedCall {
		private String method = "";
		private String url = "";
		private int status;
		private boolean matched;
		private String requestName = "";

		public String getMethod() {
			return method == null ? "" : method;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public String getUrl() {
			return url == null ? "" : url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public int getStatus() {
			return status;
		}

		public void setStatus(int status) {
			this.status = status;
		}

		public boolean isMatched() {
			return matched;
		}

		public void setMatched(boolean matched) {
			this.matched = matched;
		}

		public String getRequestName() {
			return requestName == null ? "" : requestName;
		}

		public void setRequestName(String requestName) {
			this.requestName = requestName;
		}
	}

}
