package com.apiflow.service;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public final class NtlmAuth {

	private NtlmAuth() {
	}

	public static void enable(String username, String password) {
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password.toCharArray());
			}
		});
		System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
		System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
	}

	public static void disable() {
		Authenticator.setDefault(null);
	}

}
