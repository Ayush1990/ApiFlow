package com.apiflow.model;

public class IdentitySettings {

	private boolean enterpriseAuthEnabled;
	private String samlEntityId = "apiflow";
	private String samlIdpUrl = "";
	private String samlCertificate = "";
	private String samlAcsUrl = "http://localhost:8080/api/auth/saml/acs";
	private String scimToken = "";
	private String gitProviderToken = "";

	public boolean isEnterpriseAuthEnabled() {
		return enterpriseAuthEnabled;
	}

	public void setEnterpriseAuthEnabled(boolean enterpriseAuthEnabled) {
		this.enterpriseAuthEnabled = enterpriseAuthEnabled;
	}

	public String getSamlEntityId() {
		return samlEntityId == null || samlEntityId.isBlank() ? "apiflow" : samlEntityId;
	}

	public void setSamlEntityId(String samlEntityId) {
		this.samlEntityId = samlEntityId;
	}

	public String getSamlIdpUrl() {
		return samlIdpUrl == null ? "" : samlIdpUrl;
	}

	public void setSamlIdpUrl(String samlIdpUrl) {
		this.samlIdpUrl = samlIdpUrl;
	}

	public String getSamlCertificate() {
		return samlCertificate == null ? "" : samlCertificate;
	}

	public void setSamlCertificate(String samlCertificate) {
		this.samlCertificate = samlCertificate;
	}

	public String getSamlAcsUrl() {
		return samlAcsUrl == null || samlAcsUrl.isBlank() ? "http://localhost:8080/api/auth/saml/acs" : samlAcsUrl;
	}

	public void setSamlAcsUrl(String samlAcsUrl) {
		this.samlAcsUrl = samlAcsUrl;
	}

	public String getScimToken() {
		return scimToken == null ? "" : scimToken;
	}

	public void setScimToken(String scimToken) {
		this.scimToken = scimToken;
	}

	public String getGitProviderToken() {
		return gitProviderToken == null ? "" : gitProviderToken;
	}

	public void setGitProviderToken(String gitProviderToken) {
		this.gitProviderToken = gitProviderToken;
	}

}
