package com.apiflow.model;

public class KeyValue {

	private String key = "";
	private String value = "";
	private boolean enabled = true;
	private boolean secret;

	public KeyValue() {
	}

	public KeyValue(String key, String value, boolean enabled) {
		this.key = key;
		this.value = value;
		this.enabled = enabled;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isSecret() {
		return secret;
	}

	public void setSecret(boolean secret) {
		this.secret = secret;
	}

}
