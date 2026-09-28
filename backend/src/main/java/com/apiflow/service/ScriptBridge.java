package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.StoredCookie;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

@Component
public class ScriptBridge {

	private final WorkspaceService workspaceService;
	private final FileStore store;
	private volatile List<StoredCookie> cookies = List.of();

	public ScriptBridge(WorkspaceService workspaceService, FileStore store) {
		this.workspaceService = workspaceService;
		this.store = store;
		ScriptRunner.setBridge(this);
	}

	public void bindCookies(List<StoredCookie> jar) {
		cookies = jar == null ? List.of() : List.copyOf(jar);
	}

	public ScriptCookies cookies() {
		return new ScriptCookies(cookies);
	}

	public String getProcessEnv(String name) {
		if (name == null || name.isBlank()) {
			return "";
		}
		String value = System.getenv(name.trim());
		return value == null ? "" : value;
	}

	public ExecuteResult runRequest(String collectionRef, String requestRef) {
		if (collectionRef == null || collectionRef.isBlank() || requestRef == null || requestRef.isBlank()) {
			throw new IllegalArgumentException("bru.runRequest(collection, request) requires both names");
		}
		Workspace workspace = store.copy();
		RequestCollection collection = findCollection(workspace, collectionRef.trim());
		ApiRequest request = findRequest(collection, requestRef.trim());
		ExecuteCommand command = WorkspaceService.commandFrom(request, collection.getId(), "");
		ScriptScope.timeline("script", "bru.runRequest started", collectionRef.trim() + " / " + requestRef.trim());
		ExecuteResult result = workspaceService.execute(command);
		String detail = request.getMethod() + " → " + (result.isOk() ? result.getStatus() + " " + result.getStatusText() : result.getError());
		ScriptScope.timeline("http", "Script HTTP: " + requestRef.trim(), detail);
		return result;
	}

	private static RequestCollection findCollection(Workspace workspace, String ref) {
		for (RequestCollection collection : workspace.getCollections()) {
			if (ref.equalsIgnoreCase(collection.getId()) || ref.equalsIgnoreCase(collection.getName())) {
				return collection;
			}
		}
		throw new IllegalArgumentException("Collection not found: " + ref);
	}

	private static ApiRequest findRequest(RequestCollection collection, String ref) {
		for (ApiRequest request : collection.getRequests()) {
			if (ref.equalsIgnoreCase(request.getId()) || ref.equalsIgnoreCase(request.getName())) {
				return request;
			}
		}
		throw new IllegalArgumentException("Request not found: " + ref);
	}

	public static final class ScriptCookies {

		private final List<StoredCookie> jar;

		ScriptCookies(List<StoredCookie> jar) {
			this.jar = new ArrayList<>(jar == null ? List.of() : jar);
		}

		@org.graalvm.polyglot.HostAccess.Export
		public String get(String name) {
			if (name == null) {
				return "";
			}
			for (StoredCookie cookie : jar) {
				if (name.equalsIgnoreCase(cookie.getName())) {
					return cookie.getValue() == null ? "" : cookie.getValue();
				}
			}
			return "";
		}

		@org.graalvm.polyglot.HostAccess.Export
		public void set(String name, String value, String domain) {
			if (name == null || name.isBlank()) {
				return;
			}
			StoredCookie cookie = new StoredCookie();
			cookie.setName(name.trim());
			cookie.setValue(value == null ? "" : value);
			cookie.setDomain(domain == null || domain.isBlank() ? "localhost" : domain.trim());
			cookie.setPath("/");
			CookieJar.remember(jar, cookie);
		}

		@org.graalvm.polyglot.HostAccess.Export
		public void clear() {
			jar.clear();
		}

		@org.graalvm.polyglot.HostAccess.Export
		public List<String> names() {
			List<String> names = new ArrayList<>();
			for (StoredCookie cookie : jar) {
				if (cookie.getName() != null && !cookie.getName().isBlank()) {
					names.add(cookie.getName());
				}
			}
			return names;
		}

		@org.graalvm.polyglot.HostAccess.Export
		public String header(String host) {
			return CookieJar.header(CookieJar.matching(jar, host == null ? "" : host.toLowerCase(Locale.ROOT)));
		}

	}

}
