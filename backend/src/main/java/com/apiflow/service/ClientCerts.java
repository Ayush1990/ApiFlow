package com.apiflow.service;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.apiflow.model.RequestExtras;

public final class ClientCerts {

	private static final Pattern PEM = Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----([A-Za-z0-9+/=\\r\\n]+)-----END \\1-----");

	private ClientCerts() {
	}

	public static SSLContext context(RequestExtras extras) {
		if (extras == null) {
			return null;
		}
		if (extras.getClientCertBase64().isBlank() && !extras.getCaCertBase64().isBlank()) {
			try {
				return trustStore(extras.getCaCertBase64());
			}
			catch (Exception ex) {
				throw new IllegalArgumentException("Could not load the custom CA certificate.");
			}
		}
		if (extras.getClientCertBase64().isBlank()) {
			return null;
		}
		try {
			byte[] material = decode(extras.getClientCertBase64());
			byte[] keyFile = extras.getClientKeyBase64().isBlank() ? null : decode(extras.getClientKeyBase64());
			String text = new String(material, java.nio.charset.StandardCharsets.ISO_8859_1);
			String keyText = keyFile == null ? "" : new String(keyFile, java.nio.charset.StandardCharsets.ISO_8859_1);
			if (text.contains("-----BEGIN ") || keyText.contains("-----BEGIN ")) {
				return fromPem(text + "\n" + keyText, extras.getClientCertPassword());
			}
			return fromPkcs12(material, extras.getClientCertPassword());
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not read the client certificate. Use a .p12 file, or a PEM certificate with a PEM private key.");
		}
	}

	private static SSLContext fromPkcs12(byte[] bytes, String password) throws Exception {
		char[] secret = password == null ? new char[0] : password.toCharArray();
		KeyStore store = KeyStore.getInstance("PKCS12");
		store.load(new ByteArrayInputStream(bytes), secret);
		return context(store, secret);
	}

	private static SSLContext fromPem(String text, String password) throws Exception {
		List<Certificate> chain = new ArrayList<>();
		PrivateKey key = null;
		CertificateFactory certificates = CertificateFactory.getInstance("X.509");
		Matcher matcher = PEM.matcher(text);
		while (matcher.find()) {
			String type = matcher.group(1);
			byte[] der = Base64.getMimeDecoder().decode(matcher.group(2).replaceAll("\\s", ""));
			if (type.contains("CERTIFICATE")) {
				chain.add(certificates.generateCertificate(new ByteArrayInputStream(der)));
			}
			else if (type.contains("PRIVATE KEY")) {
				key = privateKey(type, der);
			}
		}
		if (chain.isEmpty() || key == null) {
			throw new IllegalArgumentException("PEM needs a certificate and a private key. A .p12 file can hold both.");
		}
		char[] secret = password == null || password.isBlank() ? new char[0] : password.toCharArray();
		KeyStore store = KeyStore.getInstance("PKCS12");
		store.load(null, null);
		store.setKeyEntry("client", key, secret, chain.toArray(Certificate[]::new));
		return context(store, secret);
	}

	private static PrivateKey privateKey(String type, byte[] der) throws Exception {
		if (type.contains("ENCRYPTED")) {
			throw new IllegalArgumentException("Encrypted PEM keys are not supported. Decrypt the key, or use a .p12 file.");
		}
		if ("EC PRIVATE KEY".equals(type)) {
			throw new IllegalArgumentException("Use a PKCS#8 key (BEGIN PRIVATE KEY) or a .p12 file.");
		}
		byte[] pkcs8 = "RSA PRIVATE KEY".equals(type) ? wrapPkcs1(der) : der;
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		}
		catch (Exception ex) {
			return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		}
	}

	static byte[] wrapPkcs1(byte[] pkcs1) {
		byte[] version = new byte[] { 0x02, 0x01, 0x00 };
		byte[] algorithm = new byte[] {
			0x30, 0x0d,
			0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
			0x05, 0x00
		};
		return der(0x30, concat(version, algorithm, der(0x04, pkcs1)));
	}

	private static byte[] der(int tag, byte[] value) {
		byte[] length = derLength(value.length);
		byte[] out = new byte[1 + length.length + value.length];
		out[0] = (byte) tag;
		System.arraycopy(length, 0, out, 1, length.length);
		System.arraycopy(value, 0, out, 1 + length.length, value.length);
		return out;
	}

	private static byte[] derLength(int length) {
		if (length < 128) {
			return new byte[] { (byte) length };
		}
		if (length < 256) {
			return new byte[] { (byte) 0x81, (byte) length };
		}
		return new byte[] { (byte) 0x82, (byte) (length >> 8), (byte) length };
	}

	private static byte[] concat(byte[]... parts) {
		int size = 0;
		for (byte[] part : parts) {
			size += part.length;
		}
		byte[] out = new byte[size];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, out, offset, part.length);
			offset += part.length;
		}
		return out;
	}

	private static SSLContext context(KeyStore store, char[] password) throws Exception {
		KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		factory.init(store, password);
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(factory.getKeyManagers(), null, null);
		return context;
	}

	private static byte[] decode(String value) {
		return Base64.getDecoder().decode(value.replaceAll("\\s", ""));
	}

	private static SSLContext trustStore(String caCertBase64) throws Exception {
		CertificateFactory factory = CertificateFactory.getInstance("X.509");
		byte[] bytes = decode(caCertBase64);
		String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
		List<Certificate> certs = new ArrayList<>();
		if (text.contains("-----BEGIN ")) {
			Matcher matcher = PEM.matcher(text);
			while (matcher.find()) {
				if (matcher.group(1).contains("CERTIFICATE")) {
					certs.add(factory.generateCertificate(new ByteArrayInputStream(Base64.getMimeDecoder().decode(matcher.group(2).replaceAll("\\s", "")))));
				}
			}
		}
		else {
			certs.add(factory.generateCertificate(new ByteArrayInputStream(bytes)));
		}
		KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
		store.load(null, null);
		for (int index = 0; index < certs.size(); index++) {
			store.setCertificateEntry("ca-" + index, certs.get(index));
		}
		javax.net.ssl.TrustManagerFactory trust = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
		trust.init(store);
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, trust.getTrustManagers(), null);
		return context;
	}

}
