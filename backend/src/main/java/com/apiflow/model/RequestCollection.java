package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class RequestCollection {

	private String id;
	private String name;
	private List<KeyValue> variables = new ArrayList<>();
	private List<Folder> folders = new ArrayList<>();
	private List<ApiRequest> requests = new ArrayList<>();
	private Defaults defaults = new Defaults();
	private String docs = "";
	private String preRequestScript = "";
	private String postResponseScript = "";
	private String openApiSpec = "";
	private String specId = "";
	private List<TypedField> typedParams = new ArrayList<>();
	private List<TypedField> typedHeaders = new ArrayList<>();
	private String bodySchema = "";
	private List<MockScenario> mockScenarios = new ArrayList<>();
	private String mockScript = "";
	private boolean docsPublic;
	private String publishedDocsUrl = "";

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

	public List<ApiRequest> getRequests() {
		if (requests == null) {
			requests = new ArrayList<>();
		}
		return requests;
	}

	public void setRequests(List<ApiRequest> requests) {
		this.requests = requests;
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

	public List<Folder> getFolders() {
		if (folders == null) {
			folders = new ArrayList<>();
		}
		return folders;
	}

	public void setFolders(List<Folder> folders) {
		this.folders = folders;
	}

	public Defaults getDefaults() {
		if (defaults == null) {
			defaults = new Defaults();
		}
		return defaults;
	}

	public void setDefaults(Defaults defaults) {
		this.defaults = defaults;
	}

	public String getDocs() {
		return docs == null ? "" : docs;
	}

	public void setDocs(String docs) {
		this.docs = docs;
	}

	public String getPreRequestScript() {
		return preRequestScript == null ? "" : preRequestScript;
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

	public String getOpenApiSpec() {
		return openApiSpec == null ? "" : openApiSpec;
	}

	public void setOpenApiSpec(String openApiSpec) {
		this.openApiSpec = openApiSpec;
	}

	public String getSpecId() {
		return specId == null ? "" : specId;
	}

	public void setSpecId(String specId) {
		this.specId = specId;
	}

	public List<TypedField> getTypedParams() {
		if (typedParams == null) {
			typedParams = new ArrayList<>();
		}
		return typedParams;
	}

	public void setTypedParams(List<TypedField> typedParams) {
		this.typedParams = typedParams;
	}

	public List<TypedField> getTypedHeaders() {
		if (typedHeaders == null) {
			typedHeaders = new ArrayList<>();
		}
		return typedHeaders;
	}

	public void setTypedHeaders(List<TypedField> typedHeaders) {
		this.typedHeaders = typedHeaders;
	}

	public String getBodySchema() {
		return bodySchema == null ? "" : bodySchema;
	}

	public void setBodySchema(String bodySchema) {
		this.bodySchema = bodySchema;
	}

	public List<MockScenario> getMockScenarios() {
		if (mockScenarios == null) {
			mockScenarios = new ArrayList<>();
		}
		return mockScenarios;
	}

	public void setMockScenarios(List<MockScenario> mockScenarios) {
		this.mockScenarios = mockScenarios;
	}

	public String getMockScript() {
		return mockScript == null ? "" : mockScript;
	}

	public void setMockScript(String mockScript) {
		this.mockScript = mockScript;
	}

	public boolean isDocsPublic() {
		return docsPublic;
	}

	public void setDocsPublic(boolean docsPublic) {
		this.docsPublic = docsPublic;
	}

	public String getPublishedDocsUrl() {
		return publishedDocsUrl == null ? "" : publishedDocsUrl;
	}

	public void setPublishedDocsUrl(String publishedDocsUrl) {
		this.publishedDocsUrl = publishedDocsUrl;
	}

}
