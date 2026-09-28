package com.apiflow.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.KeyValue;

public class ScriptReq {

	private final ExecuteCommand command;
	private final Map<String, String> variables;

	ScriptReq(ExecuteCommand command, Map<String, String> variables) {
		this.command = command;
		this.variables = variables;
	}

	@HostAccess.Export
	public String getUrl() {
		return command == null || command.getUrl() == null ? "" : command.getUrl();
	}

	@HostAccess.Export
	public void setUrl(String url) {
		if (command != null) {
			command.setUrl(url == null ? "" : url);
		}
	}

	@HostAccess.Export
	public String getMethod() {
		return command == null || command.getMethod() == null ? "GET" : command.getMethod();
	}

	@HostAccess.Export
	public void setMethod(String method) {
		if (command != null) {
			command.setMethod(method == null ? "GET" : method);
		}
	}

	@HostAccess.Export
	public String getName() {
		return command == null || command.getRequestName() == null ? "" : command.getRequestName();
	}

	@HostAccess.Export
	public String getHost() {
		try {
			return URI.create(getUrl()).getHost();
		}
		catch (Exception ex) {
			return "";
		}
	}

	@HostAccess.Export
	public String getPath() {
		try {
			return URI.create(getUrl()).getPath();
		}
		catch (Exception ex) {
			return "";
		}
	}

	@HostAccess.Export
	public String getQueryString() {
		try {
			return URI.create(getUrl()).getQuery() == null ? "" : URI.create(getUrl()).getQuery();
		}
		catch (Exception ex) {
			return "";
		}
	}

	@HostAccess.Export
	public String getBody() {
		return command == null || command.getBody() == null ? "" : command.getBody();
	}

	@HostAccess.Export
	public void setBody(String body) {
		if (command != null) {
			command.setBody(body == null ? "" : body);
		}
	}

	@HostAccess.Export
	public String getHeader(String name) {
		if (command == null || name == null) {
			return "";
		}
		for (KeyValue header : command.getHeaders()) {
			if (name.equalsIgnoreCase(header.getKey())) {
				return header.getValue() == null ? "" : header.getValue();
			}
		}
		return "";
	}

	@HostAccess.Export
	public List<Map<String, String>> getHeaders() {
		List<Map<String, String>> headers = new ArrayList<>();
		if (command == null) {
			return headers;
		}
		for (KeyValue header : command.getHeaders()) {
			if (header.isEnabled() && header.getKey() != null) {
				headers.add(Map.of("name", header.getKey(), "value", header.getValue() == null ? "" : header.getValue()));
			}
		}
		return headers;
	}

	@HostAccess.Export
	public void setHeader(String name, String value) {
		if (command == null || name == null || name.isBlank()) {
			return;
		}
		for (KeyValue header : command.getHeaders()) {
			if (name.equalsIgnoreCase(header.getKey())) {
				header.setValue(value == null ? "" : value);
				header.setEnabled(true);
				return;
			}
		}
		command.getHeaders().add(new KeyValue(name, value == null ? "" : value, true));
	}

	@HostAccess.Export
	public void deleteHeader(String name) {
		if (command == null || name == null) {
			return;
		}
		command.getHeaders().removeIf(header -> name.equalsIgnoreCase(header.getKey()));
	}

	@HostAccess.Export
	public void setTimeout(long ms) {
		if (command != null) {
			command.setTimeoutSeconds((int) Math.max(1, ms / 1000));
		}
	}

	@HostAccess.Export
	public long getTimeout() {
		return command == null ? 30000 : command.getTimeoutSeconds() * 1000L;
	}

	@HostAccess.Export
	public String getExecutionMode() {
		return RunnerContext.current() == null ? "standalone" : "runner";
	}

	@HostAccess.Export
	public List<String> getTags() {
		if (command == null || command.getTags() == null) {
			return List.of();
		}
		return command.getTags();
	}

	@HostAccess.Export
	public String getAuthMode() {
		return command == null || command.getAuthType() == null ? "none" : command.getAuthType();
	}

	@HostAccess.Export
	public void setHeaders(Map<String, String> headers) {
		if (command == null || headers == null) {
			return;
		}
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			setHeader(entry.getKey(), entry.getValue());
		}
	}

	@HostAccess.Export
	public List<Map<String, String>> getPathParams() {
		return List.of();
	}

	@HostAccess.Export
	public void setMaxRedirects(int count) {
		if (command != null) {
			command.setFollowRedirects(count != 0);
		}
	}

	@HostAccess.Export
	public void disableParsingResponseJson() {
	}

}
