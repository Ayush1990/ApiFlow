package com.apiflow.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorkspaceSettings {

	private String proxyUrl = "";
	private String proxyBypass = "";
	private String theme = "dark";
	private boolean storeHistoryBodies = true;
	private String sendKeybinding = "mod+Enter";
	private String dotenvPath = ".env";
	private boolean nativeBruStorage;
	private String scriptMode = "safe";
	private String defaultStorageFormat = "json";
	private String aiApiKey = "";
	private String aiAnthropicKey = "";
	private String aiProvider = "openai";
	private String aiModel = "";
	private String aiCustomUrl = "";
	private SecretManagerSettings secretManager = new SecretManagerSettings();
	private IdentitySettings identity = new IdentitySettings();
	private String gitCollectionId = "";
	private Map<String, String> keybindings = new LinkedHashMap<>();
	private String clientCertBase64 = "";
	private String clientKeyBase64 = "";
	private String clientCertPassword = "";
	private String visibility = "private";
	private String customDomain = "";
	private String staticIpRanges = "";
	private String regions = "local";

	public String getProxyUrl() {
		return proxyUrl == null ? "" : proxyUrl;
	}

	public void setProxyUrl(String proxyUrl) {
		this.proxyUrl = proxyUrl;
	}

	public String getProxyBypass() {
		return proxyBypass == null ? "" : proxyBypass;
	}

	public void setProxyBypass(String proxyBypass) {
		this.proxyBypass = proxyBypass;
	}

	public String getTheme() {
		return theme == null || theme.isBlank() ? "dark" : theme;
	}

	public void setTheme(String theme) {
		this.theme = theme;
	}

	public boolean isStoreHistoryBodies() {
		return storeHistoryBodies;
	}

	public void setStoreHistoryBodies(boolean storeHistoryBodies) {
		this.storeHistoryBodies = storeHistoryBodies;
	}

	public String getSendKeybinding() {
		return sendKeybinding == null || sendKeybinding.isBlank() ? "mod+Enter" : sendKeybinding;
	}

	public void setSendKeybinding(String sendKeybinding) {
		this.sendKeybinding = sendKeybinding;
	}

	public String getDotenvPath() {
		return dotenvPath == null || dotenvPath.isBlank() ? ".env" : dotenvPath;
	}

	public void setDotenvPath(String dotenvPath) {
		this.dotenvPath = dotenvPath;
	}

	public boolean isNativeBruStorage() {
		return nativeBruStorage;
	}

	public void setNativeBruStorage(boolean nativeBruStorage) {
		this.nativeBruStorage = nativeBruStorage;
	}

	public String getScriptMode() {
		return scriptMode == null || scriptMode.isBlank() ? "safe" : scriptMode;
	}

	public void setScriptMode(String scriptMode) {
		this.scriptMode = scriptMode;
	}

	public String getDefaultStorageFormat() {
		if (defaultStorageFormat == null || defaultStorageFormat.isBlank()) {
			return nativeBruStorage ? "bru" : "json";
		}
		return defaultStorageFormat;
	}

	public void setDefaultStorageFormat(String defaultStorageFormat) {
		this.defaultStorageFormat = defaultStorageFormat;
	}

	public String getAiApiKey() {
		return aiApiKey == null ? "" : aiApiKey;
	}

	public void setAiApiKey(String aiApiKey) {
		this.aiApiKey = aiApiKey;
	}

	public String getAiAnthropicKey() {
		return aiAnthropicKey == null ? "" : aiAnthropicKey;
	}

	public void setAiAnthropicKey(String aiAnthropicKey) {
		this.aiAnthropicKey = aiAnthropicKey;
	}

	public String getAiProvider() {
		return aiProvider == null || aiProvider.isBlank() ? "openai" : aiProvider;
	}

	public void setAiProvider(String aiProvider) {
		this.aiProvider = aiProvider;
	}

	public String getAiModel() {
		return aiModel == null ? "" : aiModel;
	}

	public void setAiModel(String aiModel) {
		this.aiModel = aiModel;
	}

	public String getAiCustomUrl() {
		return aiCustomUrl == null ? "" : aiCustomUrl;
	}

	public void setAiCustomUrl(String aiCustomUrl) {
		this.aiCustomUrl = aiCustomUrl;
	}

	public SecretManagerSettings getSecretManager() {
		if (secretManager == null) {
			secretManager = new SecretManagerSettings();
		}
		return secretManager;
	}

	public void setSecretManager(SecretManagerSettings secretManager) {
		this.secretManager = secretManager;
	}

	public IdentitySettings getIdentity() {
		if (identity == null) {
			identity = new IdentitySettings();
		}
		return identity;
	}

	public void setIdentity(IdentitySettings identity) {
		this.identity = identity;
	}

	public String getGitCollectionId() {
		return gitCollectionId == null ? "" : gitCollectionId;
	}

	public void setGitCollectionId(String gitCollectionId) {
		this.gitCollectionId = gitCollectionId;
	}

	public Map<String, String> getKeybindings() {
		if (keybindings == null) {
			keybindings = new LinkedHashMap<>();
		}
		return keybindings;
	}

	public void setKeybindings(Map<String, String> keybindings) {
		this.keybindings = keybindings;
	}

	public String getClientCertBase64() {
		return clientCertBase64 == null ? "" : clientCertBase64;
	}

	public void setClientCertBase64(String clientCertBase64) {
		this.clientCertBase64 = clientCertBase64;
	}

	public String getClientKeyBase64() {
		return clientKeyBase64 == null ? "" : clientKeyBase64;
	}

	public void setClientKeyBase64(String clientKeyBase64) {
		this.clientKeyBase64 = clientKeyBase64;
	}

	public String getClientCertPassword() {
		return clientCertPassword == null ? "" : clientCertPassword;
	}

	public void setClientCertPassword(String clientCertPassword) {
		this.clientCertPassword = clientCertPassword;
	}

	public String getVisibility() {
		return visibility == null || visibility.isBlank() ? "private" : visibility;
	}

	public void setVisibility(String visibility) {
		this.visibility = visibility;
	}

	public String getCustomDomain() {
		return customDomain == null ? "" : customDomain;
	}

	public void setCustomDomain(String customDomain) {
		this.customDomain = customDomain;
	}

	public String getStaticIpRanges() {
		return staticIpRanges == null ? "" : staticIpRanges;
	}

	public void setStaticIpRanges(String staticIpRanges) {
		this.staticIpRanges = staticIpRanges;
	}

	public String getRegions() {
		return regions == null || regions.isBlank() ? "local" : regions;
	}

	public void setRegions(String regions) {
		this.regions = regions;
	}

}
