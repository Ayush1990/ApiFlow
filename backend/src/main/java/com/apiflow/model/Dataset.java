package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class Dataset {

	private String id;
	private String name;
	private String sourceType = "csv";
	private String content = "";
	private String sqlView = "SELECT * FROM data";
	private String jdbcUrl = "";
	private String jdbcUser = "";
	private String jdbcPassword = "";

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

	public String getSourceType() {
		return sourceType == null ? "csv" : sourceType;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public String getContent() {
		return content == null ? "" : content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getSqlView() {
		return sqlView == null || sqlView.isBlank() ? "SELECT * FROM data" : sqlView;
	}

	public void setSqlView(String sqlView) {
		this.sqlView = sqlView;
	}

	public String getJdbcUrl() {
		return jdbcUrl == null ? "" : jdbcUrl;
	}

	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}

	public String getJdbcUser() {
		return jdbcUser == null ? "" : jdbcUser;
	}

	public void setJdbcUser(String jdbcUser) {
		this.jdbcUser = jdbcUser;
	}

	public String getJdbcPassword() {
		return jdbcPassword == null ? "" : jdbcPassword;
	}

	public void setJdbcPassword(String jdbcPassword) {
		this.jdbcPassword = jdbcPassword;
	}

	public static class Row {
		private final List<String> columns = new ArrayList<>();
		private final List<String> values = new ArrayList<>();

		public List<String> getColumns() {
			return columns;
		}

		public List<String> getValues() {
			return values;
		}
	}

}
