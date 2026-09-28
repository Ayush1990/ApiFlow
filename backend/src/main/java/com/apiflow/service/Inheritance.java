package com.apiflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.apiflow.model.Defaults;
import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.Folder;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.RequestExtras;

public final class Inheritance {

	private Inheritance() {
	}

	public static void apply(RequestCollection collection, ExecuteCommand command) {
		if (collection == null || command == null) {
			return;
		}
		Map<String, KeyValue> headers = new LinkedHashMap<>();
		put(headers, collection.getDefaults().getHeaders());
		for (Folder folder : chain(collection, command.getFolderId())) {
			put(headers, folder.getDefaults().getHeaders());
		}
		put(headers, command.getHeaders());
		command.setHeaders(new ArrayList<>(headers.values()));
		fillCertificate(collection, command);

		String authType = command.getAuthType();
		if (authType != null && !authType.isBlank() && !"inherit".equalsIgnoreCase(authType)) {
			return;
		}
		Defaults chosen = null;
		List<Folder> folders = chain(collection, command.getFolderId());
		for (int index = folders.size() - 1; index >= 0; index--) {
			if (active(folders.get(index).getDefaults())) {
				chosen = folders.get(index).getDefaults();
				break;
			}
		}
		if (chosen == null && active(collection.getDefaults())) {
			chosen = collection.getDefaults();
		}
		if (chosen == null) {
			command.setAuthType("none");
			return;
		}
		command.setAuthType(chosen.getAuthType());
		command.setAuthToken(chosen.getAuthToken());
		command.setAuthUsername(chosen.getAuthUsername());
		command.setAuthPassword(chosen.getAuthPassword());
		command.setApiKeyName(chosen.getApiKeyName());
		command.setApiKeyValue(chosen.getApiKeyValue());
		command.setApiKeyIn(chosen.getApiKeyIn());
		if ("oauth2".equalsIgnoreCase(chosen.getAuthType())) {
			RequestExtras extras = chosen.getExtras().copy();
			if (!command.getExtras().getClientCertBase64().isBlank()) {
				extras.setClientCertBase64(command.getExtras().getClientCertBase64());
				extras.setClientKeyBase64(command.getExtras().getClientKeyBase64());
				extras.setClientCertPassword(command.getExtras().getClientCertPassword());
			}
			extras.setMockEnabled(command.getExtras().isMockEnabled());
			extras.setMockStatus(command.getExtras().getMockStatus());
			extras.setMockBody(command.getExtras().getMockBody());
			extras.setMockContentType(command.getExtras().getMockContentType());
			command.setExtras(extras);
		}
	}

	private static void fillCertificate(RequestCollection collection, ExecuteCommand command) {
		if (!command.getExtras().getClientCertBase64().isBlank()) {
			return;
		}
		List<Folder> folders = chain(collection, command.getFolderId());
		for (int index = folders.size() - 1; index >= 0; index--) {
			RequestExtras extras = folders.get(index).getDefaults().getExtras();
			if (!extras.getClientCertBase64().isBlank()) {
				command.getExtras().setClientCertBase64(extras.getClientCertBase64());
				command.getExtras().setClientKeyBase64(extras.getClientKeyBase64());
				command.getExtras().setClientCertPassword(extras.getClientCertPassword());
				return;
			}
		}
		RequestExtras extras = collection.getDefaults().getExtras();
		if (!extras.getClientCertBase64().isBlank()) {
			command.getExtras().setClientCertBase64(extras.getClientCertBase64());
			command.getExtras().setClientKeyBase64(extras.getClientKeyBase64());
			command.getExtras().setClientCertPassword(extras.getClientCertPassword());
		}
	}

	private static boolean active(Defaults defaults) {
		String type = defaults.getAuthType();
		return !"none".equalsIgnoreCase(type) && !"inherit".equalsIgnoreCase(type);
	}

	private static void put(Map<String, KeyValue> headers, List<KeyValue> source) {
		if (source == null) {
			return;
		}
		for (KeyValue header : source) {
			if (header == null || header.getKey() == null || header.getKey().isBlank()) {
				continue;
			}
			headers.put(header.getKey().trim().toLowerCase(Locale.ROOT), header);
		}
	}

	static List<Folder> chain(RequestCollection collection, String folderId) {
		List<Folder> upward = new ArrayList<>();
		String current = folderId == null ? "" : folderId;
		int guard = 0;
		while (!current.isBlank() && guard++ < 30) {
			Folder folder = null;
			for (Folder candidate : collection.getFolders()) {
				if (current.equals(candidate.getId())) {
					folder = candidate;
					break;
				}
			}
			if (folder == null) {
				break;
			}
			upward.add(folder);
			if (folder.getParentId().equals(current)) {
				break;
			}
			current = folder.getParentId();
		}
		List<Folder> rootFirst = new ArrayList<>();
		for (int index = upward.size() - 1; index >= 0; index--) {
			rootFirst.add(upward.get(index));
		}
		return rootFirst;
	}

}
