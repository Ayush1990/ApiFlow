package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class MockScenario {

	private String id;
	private String name;
	private String path;
	private String method = "GET";
	private List<MockScenarioStep> steps = new ArrayList<>();

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

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getMethod() {
		return method == null ? "GET" : method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public List<MockScenarioStep> getSteps() {
		if (steps == null) {
			steps = new ArrayList<>();
		}
		return steps;
	}

	public void setSteps(List<MockScenarioStep> steps) {
		this.steps = steps;
	}

	public static class MockScenarioStep {
		private int status = 200;
		private String contentType = "application/json";
		private String body = "{}";
		private int delayMs;
		private String datasetId = "";
		private int datasetRow = -1;

		public int getStatus() {
			return status;
		}

		public void setStatus(int status) {
			this.status = status;
		}

		public String getContentType() {
			return contentType;
		}

		public void setContentType(String contentType) {
			this.contentType = contentType;
		}

		public String getBody() {
			return body;
		}

		public void setBody(String body) {
			this.body = body;
		}

		public int getDelayMs() {
			return delayMs;
		}

		public void setDelayMs(int delayMs) {
			this.delayMs = delayMs;
		}

		public String getDatasetId() {
			return datasetId == null ? "" : datasetId;
		}

		public void setDatasetId(String datasetId) {
			this.datasetId = datasetId;
		}

		public int getDatasetRow() {
			return datasetRow;
		}

		public void setDatasetRow(int datasetRow) {
			this.datasetRow = datasetRow;
		}
	}

}
