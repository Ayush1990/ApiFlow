package com.apiflow.model;

public class Folder {

	private String id;
	private String name;
	private String parentId = "";
	private int position;
	private Defaults defaults = new Defaults();
	private java.util.List<KeyValue> variables = new java.util.ArrayList<>();
	private String docs = "";
	private String preRequestScript = "";
	private String postResponseScript = "";

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

	public String getParentId() {
		return parentId == null ? "" : parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
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

	public java.util.List<KeyValue> getVariables() {
		if (variables == null) {
			variables = new java.util.ArrayList<>();
		}
		return variables;
	}

	public void setVariables(java.util.List<KeyValue> variables) {
		this.variables = variables;
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

}
