package com.apiflow.model;

public class SecretManagerSettings {

	private String provider = "none";
	private String vaultUrl = "";
	private String vaultToken = "";
	private String awsRegion = "us-east-1";
	private String azureVaultUrl = "";
	private String azureTenantId = "";
	private String azureClientId = "";
	private String azureClientSecret = "";
	private String azureAuthMode = "client_secret";
	private String gcpProjectId = "";
	private String gcpAccessToken = "";

	public String getProvider() {
		return provider == null || provider.isBlank() ? "none" : provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getVaultUrl() {
		return vaultUrl == null ? "" : vaultUrl;
	}

	public void setVaultUrl(String vaultUrl) {
		this.vaultUrl = vaultUrl;
	}

	public String getVaultToken() {
		return vaultToken == null ? "" : vaultToken;
	}

	public void setVaultToken(String vaultToken) {
		this.vaultToken = vaultToken;
	}

	public String getAwsRegion() {
		return awsRegion == null || awsRegion.isBlank() ? "us-east-1" : awsRegion;
	}

	public void setAwsRegion(String awsRegion) {
		this.awsRegion = awsRegion;
	}

	public String getAzureVaultUrl() {
		return azureVaultUrl == null ? "" : azureVaultUrl;
	}

	public void setAzureVaultUrl(String azureVaultUrl) {
		this.azureVaultUrl = azureVaultUrl;
	}

	public String getAzureTenantId() {
		return azureTenantId == null ? "" : azureTenantId;
	}

	public void setAzureTenantId(String azureTenantId) {
		this.azureTenantId = azureTenantId;
	}

	public String getAzureClientId() {
		return azureClientId == null ? "" : azureClientId;
	}

	public void setAzureClientId(String azureClientId) {
		this.azureClientId = azureClientId;
	}

	public String getAzureClientSecret() {
		return azureClientSecret == null ? "" : azureClientSecret;
	}

	public void setAzureClientSecret(String azureClientSecret) {
		this.azureClientSecret = azureClientSecret;
	}

	public String getAzureAuthMode() {
		return azureAuthMode == null || azureAuthMode.isBlank() ? "client_secret" : azureAuthMode;
	}

	public void setAzureAuthMode(String azureAuthMode) {
		this.azureAuthMode = azureAuthMode;
	}

	public String getGcpProjectId() {
		return gcpProjectId == null ? "" : gcpProjectId;
	}

	public void setGcpProjectId(String gcpProjectId) {
		this.gcpProjectId = gcpProjectId;
	}

	public String getGcpAccessToken() {
		return gcpAccessToken == null ? "" : gcpAccessToken;
	}

	public void setGcpAccessToken(String gcpAccessToken) {
		this.gcpAccessToken = gcpAccessToken;
	}

}
