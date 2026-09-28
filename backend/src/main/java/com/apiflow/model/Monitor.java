package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class Monitor {

	private String id;
	private String name;
	private String collectionId;
	private String environmentId = "";
	private String schedule = "every 5 minutes";
	private boolean enabled;
	private String triggerMode = "schedule";
	private List<String> alertEmails = new ArrayList<>();
	private String slackWebhook = "";
	private String pagerDutyKey = "";
	private String region = "auto";
	private String datasetId = "";
	private String reportTitle = "";
	private boolean hideUrls;
	private String smtpHost = "";
	private String runnerMode = "local";
	private long lastRunAt;
	private String lastStatus = "";

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

	public String getCollectionId() {
		return collectionId;
	}

	public void setCollectionId(String collectionId) {
		this.collectionId = collectionId;
	}

	public String getEnvironmentId() {
		return environmentId == null ? "" : environmentId;
	}

	public void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}

	public String getSchedule() {
		return schedule == null ? "every 5 minutes" : schedule;
	}

	public void setSchedule(String schedule) {
		this.schedule = schedule;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getTriggerMode() {
		return triggerMode == null ? "schedule" : triggerMode;
	}

	public void setTriggerMode(String triggerMode) {
		this.triggerMode = triggerMode;
	}

	public List<String> getAlertEmails() {
		if (alertEmails == null) {
			alertEmails = new ArrayList<>();
		}
		return alertEmails;
	}

	public void setAlertEmails(List<String> alertEmails) {
		this.alertEmails = alertEmails;
	}

	public String getSlackWebhook() {
		return slackWebhook == null ? "" : slackWebhook;
	}

	public void setSlackWebhook(String slackWebhook) {
		this.slackWebhook = slackWebhook;
	}

	public String getPagerDutyKey() {
		return pagerDutyKey == null ? "" : pagerDutyKey;
	}

	public void setPagerDutyKey(String pagerDutyKey) {
		this.pagerDutyKey = pagerDutyKey;
	}

	public String getRegion() {
		return region == null ? "auto" : region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getDatasetId() {
		return datasetId == null ? "" : datasetId;
	}

	public void setDatasetId(String datasetId) {
		this.datasetId = datasetId;
	}

	public String getReportTitle() {
		return reportTitle == null || reportTitle.isBlank() ? getName() : reportTitle;
	}

	public void setReportTitle(String reportTitle) {
		this.reportTitle = reportTitle;
	}

	public boolean isHideUrls() {
		return hideUrls;
	}

	public void setHideUrls(boolean hideUrls) {
		this.hideUrls = hideUrls;
	}

	public String getSmtpHost() {
		return smtpHost == null ? "" : smtpHost;
	}

	public void setSmtpHost(String smtpHost) {
		this.smtpHost = smtpHost;
	}

	public String getRunnerMode() {
		return runnerMode == null || runnerMode.isBlank() ? "local" : runnerMode;
	}

	public void setRunnerMode(String runnerMode) {
		this.runnerMode = runnerMode;
	}

	public long getLastRunAt() {
		return lastRunAt;
	}

	public void setLastRunAt(long lastRunAt) {
		this.lastRunAt = lastRunAt;
	}

	public String getLastStatus() {
		return lastStatus == null ? "" : lastStatus;
	}

	public void setLastStatus(String lastStatus) {
		this.lastStatus = lastStatus;
	}

}
