package com.apiflow.model;

public class TypedField {

	private String key;
	private String dataType = "string";
	private boolean required;
	private String enumValues = "";
	private String description = "";

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getDataType() {
		return dataType == null ? "string" : dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public String getEnumValues() {
		return enumValues == null ? "" : enumValues;
	}

	public void setEnumValues(String enumValues) {
		this.enumValues = enumValues;
	}

	public String getDescription() {
		return description == null ? "" : description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
