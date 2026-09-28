package com.apiflow.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.KeyValue;
import com.apiflow.model.SecretManagerSettings;

public final class SecretRefs {

	private static final Pattern SECRET = Pattern.compile("\\{\\{\\s*secret:([^}]+)\\s*}}");

	private SecretRefs() {
	}

	public static String resolveText(String input, SecretManagerService secrets, SecretManagerSettings settings) {
		if (input == null || input.isEmpty() || secrets == null || settings == null || "none".equalsIgnoreCase(settings.getProvider())) {
			return input == null ? "" : input;
		}
		Matcher matcher = SECRET.matcher(input);
		StringBuffer out = new StringBuffer();
		while (matcher.find()) {
			String value = secrets.resolve(matcher.group(1).trim(), settings);
			matcher.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	public static void applyCommand(ExecuteCommand command, SecretManagerService secrets, SecretManagerSettings settings) {
		if (command == null || secrets == null || settings == null || "none".equalsIgnoreCase(settings.getProvider())) {
			return;
		}
		command.setUrl(resolveText(command.getUrl(), secrets, settings));
		command.setBody(resolveText(command.getBody(), secrets, settings));
		command.setAuthToken(resolveText(command.getAuthToken(), secrets, settings));
		command.setAuthPassword(resolveText(command.getAuthPassword(), secrets, settings));
		command.setApiKeyValue(resolveText(command.getApiKeyValue(), secrets, settings));
		for (KeyValue header : command.getHeaders()) {
			header.setValue(resolveText(header.getValue(), secrets, settings));
		}
		for (KeyValue param : command.getParams()) {
			param.setValue(resolveText(param.getValue(), secrets, settings));
		}
	}

}
