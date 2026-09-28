package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.apiflow.store.FileStore;

import tools.jackson.databind.ObjectMapper;

public final class VaultStore {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final SecureRandom RANDOM = new SecureRandom();
	private static volatile FileStore files;
	private static volatile String passphrase = "";

	private VaultStore() {
	}

	public static void bind(FileStore store) {
		files = store;
	}

	public static boolean isLocked() {
		Path file = path();
		if (file == null || !Files.exists(file)) {
			return passphrase.isBlank();
		}
		try {
			String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
			return raw.startsWith("v2:") && passphrase.isBlank();
		}
		catch (Exception ex) {
			return true;
		}
	}

	public static void unlock(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("Vault passphrase is required");
		}
		String previous = passphrase;
		passphrase = secret;
		try {
			read();
		}
		catch (IllegalArgumentException ex) {
			passphrase = previous;
			throw ex;
		}
	}

	public static Map<String, String> names() {
		Map<String, String> masked = new LinkedHashMap<>();
		if (isLocked()) {
			return masked;
		}
		for (String key : read().keySet()) {
			masked.put(key, "••••••••");
		}
		return masked;
	}

	public static void put(String key, String value) {
		if (isLocked()) {
			throw new IllegalArgumentException("Unlock the vault with a passphrase first");
		}
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Vault key is required");
		}
		Map<String, String> data = read();
		data.put(key.trim(), value == null ? "" : value);
		write(data);
	}

	public static void delete(String key) {
		if (isLocked()) {
			throw new IllegalArgumentException("Unlock the vault with a passphrase first");
		}
		Map<String, String> data = read();
		data.remove(key);
		write(data);
	}

	public static String get(String key) {
		if (isLocked()) {
			return "";
		}
		return read().getOrDefault(key, "");
	}

	private static Map<String, String> read() {
		Path file = path();
		if (file == null || !Files.exists(file)) {
			return new LinkedHashMap<>();
		}
		try {
			String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
			String json;
			if (raw.startsWith("v2:")) {
				if (passphrase.isBlank()) {
					throw new IllegalArgumentException("Vault is locked");
				}
				json = new String(decrypt(Base64.getDecoder().decode(raw.substring(3))), StandardCharsets.UTF_8);
			}
			else {
				json = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
			}
			Map<?, ?> parsed = MAPPER.readValue(json, Map.class);
			Map<String, String> data = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : parsed.entrySet()) {
				data.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
			}
			return data;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not unlock the vault");
		}
	}

	private static void write(Map<String, String> data) {
		Path file = path();
		if (file == null) {
			throw new IllegalArgumentException("Vault storage is not ready");
		}
		if (passphrase.isBlank()) {
			throw new IllegalArgumentException("Unlock the vault with a passphrase first");
		}
		try {
			Files.createDirectories(file.getParent());
			byte[] cipher = encrypt(MAPPER.writeValueAsString(data).getBytes(StandardCharsets.UTF_8));
			Files.writeString(file, "v2:" + Base64.getEncoder().encodeToString(cipher), StandardCharsets.UTF_8);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not write vault");
		}
	}

	private static byte[] encrypt(byte[] plain) throws Exception {
		byte[] salt = new byte[16];
		byte[] iv = new byte[12];
		RANDOM.nextBytes(salt);
		RANDOM.nextBytes(iv);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, key(salt), new GCMParameterSpec(128, iv));
		byte[] body = cipher.doFinal(plain);
		byte[] packed = new byte[salt.length + iv.length + body.length];
		System.arraycopy(salt, 0, packed, 0, salt.length);
		System.arraycopy(iv, 0, packed, salt.length, iv.length);
		System.arraycopy(body, 0, packed, salt.length + iv.length, body.length);
		return packed;
	}

	private static byte[] decrypt(byte[] packed) throws Exception {
		byte[] salt = Arrays.copyOfRange(packed, 0, 16);
		byte[] iv = Arrays.copyOfRange(packed, 16, 28);
		byte[] body = Arrays.copyOfRange(packed, 28, packed.length);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key(salt), new GCMParameterSpec(128, iv));
		return cipher.doFinal(body);
	}

	private static SecretKeySpec key(byte[] salt) throws Exception {
		PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, 120_000, 256);
		byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
		return new SecretKeySpec(encoded, "AES");
	}

	private static Path path() {
		return files == null ? null : files.root().resolve("vault.dat");
	}

}
