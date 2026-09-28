package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class ApiRequest {

	private String id;
	private String name;
	private String method = "GET";
	private String url = "";
	private List<KeyValue> params = new ArrayList<>();
	private List<KeyValue> headers = new ArrayList<>();
	private String bodyType = "none";
	private String body = "";
	private List<KeyValue> form = new ArrayList<>();
	private String authType = "none";
	private String authToken = "";
	private String authUsername = "";
	private String authPassword = "";
	private String folderId = "";
	private int position;
	private int timeoutSeconds = 30;
	private boolean followRedirects = true;
	private String graphqlQuery = "";
	private String graphqlVariables = "";
	private List<FilePart> files = new ArrayList<>();
	private String apiKeyName = "";
	private String apiKeyValue = "";
	private String apiKeyIn = "header";
	private String preRequestScript = "";
	private String postResponseScript = "";
	private String exampleBody = "";
	private String exampleContentType = "";
	private RequestExtras extras = new RequestExtras();
	private List<Assertion> assertions = new ArrayList<>();
	private List<Extractor> extractors = new ArrayList<>();
	private List<KeyValue> variables = new ArrayList<>();
	private List<ResponseExample> examples = new ArrayList<>();
	private String docs = "";
	private List<String> tags = new ArrayList<>();

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

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public List<KeyValue> getParams() {
		if (params == null) {
			params = new ArrayList<>();
		}
		return params;
	}

	public void setParams(List<KeyValue> params) {
		this.params = params;
	}

	public List<KeyValue> getHeaders() {
		if (headers == null) {
			headers = new ArrayList<>();
		}
		return headers;
	}

	public void setHeaders(List<KeyValue> headers) {
		this.headers = headers;
	}

	public String getBodyType() {
		return bodyType;
	}

	public void setBodyType(String bodyType) {
		this.bodyType = bodyType;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public List<KeyValue> getForm() {
		if (form == null) {
			form = new ArrayList<>();
		}
		return form;
	}

	public void setForm(List<KeyValue> form) {
		this.form = form;
	}

	public String getAuthType() {
		return authType;
	}

	public void setAuthType(String authType) {
		this.authType = authType;
	}

	public String getAuthToken() {
		return authToken;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}

	public String getAuthUsername() {
		return authUsername;
	}

	public void setAuthUsername(String authUsername) {
		this.authUsername = authUsername;
	}

	public String getAuthPassword() {
		return authPassword;
	}

	public void setAuthPassword(String authPassword) {
		this.authPassword = authPassword;
	}

	public String getFolderId() {
		return folderId == null ? "" : folderId;
	}

	public void setFolderId(String folderId) {
		this.folderId = folderId;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds <= 0 ? 30 : timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public boolean isFollowRedirects() {
		return followRedirects;
	}

	public void setFollowRedirects(boolean followRedirects) {
		this.followRedirects = followRedirects;
	}

	public String getGraphqlQuery() {
		return graphqlQuery;
	}

	public void setGraphqlQuery(String graphqlQuery) {
		this.graphqlQuery = graphqlQuery;
	}

	public String getGraphqlVariables() {
		return graphqlVariables;
	}

	public void setGraphqlVariables(String graphqlVariables) {
		this.graphqlVariables = graphqlVariables;
	}

	public List<FilePart> getFiles() {
		if (files == null) {
			files = new ArrayList<>();
		}
		return files;
	}

	public void setFiles(List<FilePart> files) {
		this.files = files;
	}

	public String getApiKeyName() {
		return apiKeyName;
	}

	public void setApiKeyName(String apiKeyName) {
		this.apiKeyName = apiKeyName;
	}

	public String getApiKeyValue() {
		return apiKeyValue;
	}

	public void setApiKeyValue(String apiKeyValue) {
		this.apiKeyValue = apiKeyValue;
	}

	public String getApiKeyIn() {
		return apiKeyIn == null || apiKeyIn.isBlank() ? "header" : apiKeyIn;
	}

	public void setApiKeyIn(String apiKeyIn) {
		this.apiKeyIn = apiKeyIn;
	}

	public String getPreRequestScript() {
		return preRequestScript;
	}

	public void setPreRequestScript(String preRequestScript) {
		this.preRequestScript = preRequestScript;
	}

	public String getPostResponseScript() {
		return postResponseScript == null ? "" : postResponseScript;
	}

	public void setPostResponseScript(String postResponseScript) {
		this.postResponseScript = postResponseScript;
	}

	public String getExampleBody() {
		return exampleBody == null ? "" : exampleBody;
	}

	public void setExampleBody(String exampleBody) {
		this.exampleBody = exampleBody;
	}

	public String getExampleContentType() {
		return exampleContentType == null ? "" : exampleContentType;
	}

	public void setExampleContentType(String exampleContentType) {
		this.exampleContentType = exampleContentType;
	}

	public RequestExtras getExtras() {
		if (extras == null) {
			extras = new RequestExtras();
		}
		return extras;
	}

	public void setExtras(RequestExtras extras) {
		this.extras = extras;
	}

	public List<Assertion> getAssertions() {
		if (assertions == null) {
			assertions = new ArrayList<>();
		}
		return assertions;
	}

	public void setAssertions(List<Assertion> assertions) {
		this.assertions = assertions;
	}

	public List<Extractor> getExtractors() {
		if (extractors == null) {
			extractors = new ArrayList<>();
		}
		return extractors;
	}

	public void setExtractors(List<Extractor> extractors) {
		this.extractors = extractors;
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

	public List<ResponseExample> getExamples() {
		if (examples == null) {
			examples = new ArrayList<>();
		}
		return examples;
	}

	public void setExamples(List<ResponseExample> examples) {
		this.examples = examples;
	}

	public String getDocs() {
		return docs == null ? "" : docs;
	}

	public void setDocs(String docs) {
		this.docs = docs;
	}

	public List<String> getTags() {
		if (tags == null) {
			tags = new ArrayList<>();
		}
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

}
