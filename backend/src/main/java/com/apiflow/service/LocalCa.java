package com.apiflow.service;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

final class LocalCa {

	private final KeyPair caKey;
	private final X509Certificate caCert;

	private LocalCa(KeyPair caKey, X509Certificate caCert) {
		this.caKey = caKey;
		this.caCert = caCert;
	}

	static LocalCa loadOrCreate(Path directory) throws Exception {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
		Files.createDirectories(directory);
		Path certFile = directory.resolve("apiflow-ca.crt");
		Path keyFile = directory.resolve("apiflow-ca.key");
		if (Files.exists(certFile) && Files.exists(keyFile)) {
			return read(certFile, keyFile);
		}
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair key = generator.generateKeyPair();
		X509Certificate cert = sign(key.getPublic(), key.getPrivate(), "CN=ApiFlow Local CA", key.getPublic(), true, null);
		try (JcaPEMWriter writer = new JcaPEMWriter(Files.newBufferedWriter(certFile, StandardCharsets.UTF_8))) {
			writer.writeObject(cert);
		}
		try (JcaPEMWriter writer = new JcaPEMWriter(Files.newBufferedWriter(keyFile, StandardCharsets.UTF_8))) {
			writer.writeObject(key.getPrivate());
		}
		return new LocalCa(key, cert);
	}

	SSLContext serverContext(String host) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair leaf = generator.generateKeyPair();
		X509Certificate cert = sign(leaf.getPublic(), caKey.getPrivate(), "CN=" + host, leaf.getPublic(), false, host);
		KeyStore store = KeyStore.getInstance("PKCS12");
		store.load(null, new char[0]);
		store.setKeyEntry("leaf", leaf.getPrivate(), new char[0], new Certificate[] { cert, caCert });
		KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		factory.init(store, new char[0]);
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(factory.getKeyManagers(), null, new SecureRandom());
		return context;
	}

	Path certificateFile(Path directory) {
		return directory.resolve("apiflow-ca.crt");
	}

	private static LocalCa read(Path certFile, Path keyFile) throws Exception {
		org.bouncycastle.openssl.PEMParser certParser = new org.bouncycastle.openssl.PEMParser(Files.newBufferedReader(certFile, StandardCharsets.UTF_8));
		Object certObject = certParser.readObject();
		certParser.close();
		X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate((org.bouncycastle.cert.X509CertificateHolder) certObject);
		org.bouncycastle.openssl.PEMParser keyParser = new org.bouncycastle.openssl.PEMParser(Files.newBufferedReader(keyFile, StandardCharsets.UTF_8));
		Object keyObject = keyParser.readObject();
		keyParser.close();
		org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter converter = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter().setProvider("BC");
		java.security.PrivateKey privateKey;
		if (keyObject instanceof org.bouncycastle.openssl.PEMKeyPair pair) {
			privateKey = converter.getPrivateKey(pair.getPrivateKeyInfo());
		}
		else if (keyObject instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo info) {
			privateKey = converter.getPrivateKey(info);
		}
		else {
			throw new IllegalArgumentException("Unrecognized CA key");
		}
		java.security.PublicKey publicKey = cert.getPublicKey();
		return new LocalCa(new KeyPair(publicKey, privateKey), cert);
	}

	private static X509Certificate sign(java.security.PublicKey subjectKey, java.security.PrivateKey signer, String dn, java.security.PublicKey unused, boolean ca, String host) throws Exception {
		org.bouncycastle.asn1.x500.X500Name name = new org.bouncycastle.asn1.x500.X500Name(dn);
		org.bouncycastle.asn1.x500.X500Name issuer = ca ? name : new org.bouncycastle.asn1.x500.X500Name("CN=ApiFlow Local CA");
		JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
			issuer,
			BigInteger.valueOf(System.nanoTime()),
			Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
			Date.from(Instant.now().plus(825, ChronoUnit.DAYS)),
			name,
			subjectKey);
		if (ca) {
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
			builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
		}
		else {
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
			builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
			builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
			if (host != null && !host.isBlank()) {
				builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.dNSName, host)));
			}
		}
		ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(signer);
		return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(contentSigner));
	}

	static byte[] empty() {
		return new byte[0];
	}

	static ByteArrayInputStream emptyStream() {
		return new ByteArrayInputStream(empty());
	}

}
