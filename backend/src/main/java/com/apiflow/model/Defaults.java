package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class Defaults {

	private List<KeyValue> headers = new ArrayList<>();
	private String authType = "none";
	private String authToken = "";
	private String authUsername = "";
	private String authPassword = "";
	private String apiKeyName = "";
	private String apiKeyValue = "";
	private String apiKeyIn = "header";
	private RequestExtras extras = new RequestExtras();

	public List<KeyValue> getHeaders() {
		if (headers == null) {
			headers = new ArrayList<>();
		}
		return headers;
	}

	public void setHeaders(List<KeyValue> headers) {
		this.headers = headers;
	}

	public String getAuthType() {
		return authType == null || authType.isBlank() ? "none" : authType;
	}

	public void setAuthType(String authType) {
		this.authType = authType;
	}

	public String getAuthToken() {
		return authToken == null ? "" : authToken;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}

	public String getAuthUsername() {
		return authUsername == null ? "" : authUsername;
	}

	public void setAuthUsername(String authUsername) {
		this.authUsername = authUsername;
	}

	public String getAuthPassword() {
		return authPassword == null ? "" : authPassword;
	}

	public void setAuthPassword(String authPassword) {
		this.authPassword = authPassword;
	}

	public String getApiKeyName() {
		return apiKeyName == null ? "" : apiKeyName;
	}

	public void setApiKeyName(String apiKeyName) {
		this.apiKeyName = apiKeyName;
	}

	public String getApiKeyValue() {
		return apiKeyValue == null ? "" : apiKeyValue;
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

	public RequestExtras getExtras() {
		if (extras == null) {
			extras = new RequestExtras();
		}
		return extras;
	}

	public void setExtras(RequestExtras extras) {
		this.extras = extras;
	}

}
