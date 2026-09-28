package com.apiflow.model;

public class MonitorRun {

	private String id;
	private String monitorId;
	private long startedAt;
	private long durationMs;
	private int passed;
	private int failed;
	private boolean published;
	private String reportHtml = "";
	private String reportUrl = "";
	private String region = "";
	private String runnerAddress = "";
	private String staticRange = "";
	private boolean addressAllowed = true;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getMonitorId() {
		return monitorId;
	}

	public void setMonitorId(String monitorId) {
		this.monitorId = monitorId;
	}

	public long getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(long startedAt) {
		this.startedAt = startedAt;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public int getPassed() {
		return passed;
	}

	public void setPassed(int passed) {
		this.passed = passed;
	}

	public int getFailed() {
		return failed;
	}

	public void setFailed(int failed) {
		this.failed = failed;
	}

	public boolean isPublished() {
		return published;
	}

	public void setPublished(boolean published) {
		this.published = published;
	}

	public String getReportHtml() {
		return reportHtml == null ? "" : reportHtml;
	}

	public void setReportHtml(String reportHtml) {
		this.reportHtml = reportHtml;
	}

	public String getReportUrl() {
		return reportUrl == null ? "" : reportUrl;
	}

	public void setReportUrl(String reportUrl) {
		this.reportUrl = reportUrl;
	}

	public String getRegion() {
		return region == null ? "" : region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getRunnerAddress() {
		return runnerAddress == null ? "" : runnerAddress;
	}

	public void setRunnerAddress(String runnerAddress) {
		this.runnerAddress = runnerAddress;
	}

	public String getStaticRange() {
		return staticRange == null ? "" : staticRange;
	}

	public void setStaticRange(String staticRange) {
		this.staticRange = staticRange;
	}

	public boolean isAddressAllowed() {
		return addressAllowed;
	}

	public void setAddressAllowed(boolean addressAllowed) {
		this.addressAllowed = addressAllowed;
	}

}
