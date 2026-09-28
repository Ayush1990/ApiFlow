package com.apiflow.model;

public class PerformanceRun {

	private String id;
	private String collectionId = "";
	private String requestId = "";
	private String datasetId = "";
	private int virtualUsers;
	private int iterations;
	private long durationMs;
	private int success;
	private int failure;
	private long p50Ms;
	private long p95Ms;
	private long p99Ms;
	private double requestsPerSecond;
	private int requestCount;
	private long startedAt;
	private java.util.List<String> failures = new java.util.ArrayList<>();
	private java.util.List<String> timeline = new java.util.ArrayList<>();
	private String status = "completed";
	private int assertionPass;
	private int assertionFail;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCollectionId() {
		return collectionId == null ? "" : collectionId;
	}

	public void setCollectionId(String collectionId) {
		this.collectionId = collectionId;
	}

	public String getRequestId() {
		return requestId == null ? "" : requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public String getDatasetId() {
		return datasetId == null ? "" : datasetId;
	}

	public void setDatasetId(String datasetId) {
		this.datasetId = datasetId;
	}

	public int getVirtualUsers() {
		return virtualUsers;
	}

	public void setVirtualUsers(int virtualUsers) {
		this.virtualUsers = virtualUsers;
	}

	public int getIterations() {
		return iterations;
	}

	public void setIterations(int iterations) {
		this.iterations = iterations;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public int getSuccess() {
		return success;
	}

	public void setSuccess(int success) {
		this.success = success;
	}

	public int getFailure() {
		return failure;
	}

	public void setFailure(int failure) {
		this.failure = failure;
	}

	public long getP50Ms() {
		return p50Ms;
	}

	public void setP50Ms(long p50Ms) {
		this.p50Ms = p50Ms;
	}

	public long getP95Ms() {
		return p95Ms;
	}

	public void setP95Ms(long p95Ms) {
		this.p95Ms = p95Ms;
	}

	public long getP99Ms() {
		return p99Ms;
	}

	public void setP99Ms(long p99Ms) {
		this.p99Ms = p99Ms;
	}

	public double getRequestsPerSecond() {
		return requestsPerSecond;
	}

	public void setRequestsPerSecond(double requestsPerSecond) {
		this.requestsPerSecond = requestsPerSecond;
	}

	public int getRequestCount() {
		return requestCount;
	}

	public void setRequestCount(int requestCount) {
		this.requestCount = requestCount;
	}

	public long getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(long startedAt) {
		this.startedAt = startedAt;
	}

	public java.util.List<String> getFailures() {
		if (failures == null) {
			failures = new java.util.ArrayList<>();
		}
		return failures;
	}

	public void setFailures(java.util.List<String> failures) {
		this.failures = failures;
	}

	public java.util.List<String> getTimeline() {
		if (timeline == null) {
			timeline = new java.util.ArrayList<>();
		}
		return timeline;
	}

	public void setTimeline(java.util.List<String> timeline) {
		this.timeline = timeline;
	}

	public String getStatus() {
		return status == null ? "completed" : status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getAssertionPass() {
		return assertionPass;
	}

	public void setAssertionPass(int assertionPass) {
		this.assertionPass = assertionPass;
	}

	public int getAssertionFail() {
		return assertionFail;
	}

	public void setAssertionFail(int assertionFail) {
		this.assertionFail = assertionFail;
	}

}
