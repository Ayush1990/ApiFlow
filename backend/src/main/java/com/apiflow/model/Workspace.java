package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class Workspace {

	private List<RequestCollection> collections = new ArrayList<>();
	private List<Environment> environments = new ArrayList<>();
	private List<StoredCookie> cookies = new ArrayList<>();
	private List<HistoryEntry> history = new ArrayList<>();
	private List<KeyValue> variables = new ArrayList<>();
	private List<ApiRequest> adhocRequests = new ArrayList<>();
	private List<ApiSpec> specs = new ArrayList<>();
	private List<Monitor> monitors = new ArrayList<>();
	private List<MonitorRun> monitorRuns = new ArrayList<>();
	private List<FlowDefinition> flows = new ArrayList<>();
	private List<Dataset> datasets = new ArrayList<>();
	private List<WorkspaceDocument> documents = new ArrayList<>();
	private List<RunReport> collectionRuns = new ArrayList<>();
	private List<com.apiflow.model.PerformanceRun> performanceRuns = new ArrayList<>();
	private List<Comment> comments = new ArrayList<>();
	private List<WorkspaceMember> members = new ArrayList<>();
	private List<Webhook> webhooks = new ArrayList<>();
	private List<InventoryApp> inventory = new ArrayList<>();
	private List<ActivityEvent> activity = new ArrayList<>();
	private List<CollectionPullRequest> pullRequests = new ArrayList<>();
	private WorkspaceSettings settings = new WorkspaceSettings();

	public List<RequestCollection> getCollections() {
		if (collections == null) {
			collections = new ArrayList<>();
		}
		return collections;
	}

	public void setCollections(List<RequestCollection> collections) {
		this.collections = collections;
	}

	public List<Environment> getEnvironments() {
		if (environments == null) {
			environments = new ArrayList<>();
		}
		return environments;
	}

	public void setEnvironments(List<Environment> environments) {
		this.environments = environments;
	}

	public List<StoredCookie> getCookies() {
		if (cookies == null) {
			cookies = new ArrayList<>();
		}
		return cookies;
	}

	public void setCookies(List<StoredCookie> cookies) {
		this.cookies = cookies;
	}

	public List<HistoryEntry> getHistory() {
		if (history == null) {
			history = new ArrayList<>();
		}
		return history;
	}

	public void setHistory(List<HistoryEntry> history) {
		this.history = history;
	}

	public List<KeyValue> getVariables() {
		if (variables == null) {
			variables = new ArrayList<>();
		}
		return variables;
	}

	public void setVariables(List<KeyValue> variables) {
		this.variables = variables;
	}

	public List<ApiRequest> getAdhocRequests() {
		if (adhocRequests == null) {
			adhocRequests = new ArrayList<>();
		}
		return adhocRequests;
	}

	public void setAdhocRequests(List<ApiRequest> adhocRequests) {
		this.adhocRequests = adhocRequests;
	}

	public List<ApiSpec> getSpecs() {
		if (specs == null) {
			specs = new ArrayList<>();
		}
		return specs;
	}

	public void setSpecs(List<ApiSpec> specs) {
		this.specs = specs;
	}

	public List<Monitor> getMonitors() {
		if (monitors == null) {
			monitors = new ArrayList<>();
		}
		return monitors;
	}

	public void setMonitors(List<Monitor> monitors) {
		this.monitors = monitors;
	}

	public List<MonitorRun> getMonitorRuns() {
		if (monitorRuns == null) {
			monitorRuns = new ArrayList<>();
		}
		return monitorRuns;
	}

	public void setMonitorRuns(List<MonitorRun> monitorRuns) {
		this.monitorRuns = monitorRuns;
	}

	public List<FlowDefinition> getFlows() {
		if (flows == null) {
			flows = new ArrayList<>();
		}
		return flows;
	}

	public void setFlows(List<FlowDefinition> flows) {
		this.flows = flows;
	}

	public List<Dataset> getDatasets() {
		if (datasets == null) {
			datasets = new ArrayList<>();
		}
		return datasets;
	}

	public void setDatasets(List<Dataset> datasets) {
		this.datasets = datasets;
	}

	public List<WorkspaceDocument> getDocuments() {
		if (documents == null) {
			documents = new ArrayList<>();
		}
		return documents;
	}

	public void setDocuments(List<WorkspaceDocument> documents) {
		this.documents = documents;
	}

	public List<RunReport> getCollectionRuns() {
		if (collectionRuns == null) {
			collectionRuns = new ArrayList<>();
		}
		return collectionRuns;
	}

	public void setCollectionRuns(List<RunReport> collectionRuns) {
		this.collectionRuns = collectionRuns;
	}

	public List<PerformanceRun> getPerformanceRuns() {
		if (performanceRuns == null) {
			performanceRuns = new ArrayList<>();
		}
		return performanceRuns;
	}

	public void setPerformanceRuns(List<PerformanceRun> performanceRuns) {
		this.performanceRuns = performanceRuns;
	}

	public List<Comment> getComments() {
		if (comments == null) {
			comments = new ArrayList<>();
		}
		return comments;
	}

	public void setComments(List<Comment> comments) {
		this.comments = comments;
	}

	public List<WorkspaceMember> getMembers() {
		if (members == null) {
			members = new ArrayList<>();
		}
		return members;
	}

	public void setMembers(List<WorkspaceMember> members) {
		this.members = members;
	}

	public List<Webhook> getWebhooks() {
		if (webhooks == null) {
			webhooks = new ArrayList<>();
		}
		return webhooks;
	}

	public void setWebhooks(List<Webhook> webhooks) {
		this.webhooks = webhooks;
	}

	public List<InventoryApp> getInventory() {
		if (inventory == null) {
			inventory = new ArrayList<>();
		}
		return inventory;
	}

	public void setInventory(List<InventoryApp> inventory) {
		this.inventory = inventory;
	}

	public List<ActivityEvent> getActivity() {
		if (activity == null) {
			activity = new ArrayList<>();
		}
		return activity;
	}

	public void setActivity(List<ActivityEvent> activity) {
		this.activity = activity;
	}

	public List<CollectionPullRequest> getPullRequests() {
		if (pullRequests == null) {
			pullRequests = new ArrayList<>();
		}
		return pullRequests;
	}

	public void setPullRequests(List<CollectionPullRequest> pullRequests) {
		this.pullRequests = pullRequests;
	}

	public WorkspaceSettings getSettings() {
		if (settings == null) {
			settings = new WorkspaceSettings();
		}
		return settings;
	}

	public void setSettings(WorkspaceSettings settings) {
		this.settings = settings;
	}

}
