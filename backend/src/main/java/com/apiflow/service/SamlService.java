package com.apiflow.service;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.apiflow.model.IdentitySettings;

@Service
public class SamlService {

	public String metadata(IdentitySettings settings) {
		return """
				<?xml version="1.0"?>
				<EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata" entityID="%s">
				  <SPSSODescriptor AuthnRequestsSigned="false" WantAssertionsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
				    <NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>
				    <AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="%s" index="1"/>
				  </SPSSODescriptor>
				</EntityDescriptor>
				""".formatted(settings.getSamlEntityId(), settings.getSamlAcsUrl());
	}

	public String loginRedirect(IdentitySettings settings) {
		if (settings.getSamlIdpUrl().isBlank()) {
			throw new IllegalArgumentException("Configure SAML IdP URL in workspace settings");
		}
		String requestId = "_" + UUID.randomUUID();
		String samlRequest = Base64.getEncoder().encodeToString((
			"<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ID=\"" + requestId + "\" Version=\"2.0\" IssueInstant=\"" + java.time.Instant.now() + "\" AssertionConsumerServiceURL=\"" + settings.getSamlAcsUrl() + "\" ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\"><saml:Issuer xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">" + settings.getSamlEntityId() + "</saml:Issuer></samlp:AuthnRequest>"
		).getBytes(StandardCharsets.UTF_8));
		String separator = settings.getSamlIdpUrl().contains("?") ? "&" : "?";
		return settings.getSamlIdpUrl() + separator + "SAMLRequest=" + encode(samlRequest) + "&RelayState=apiflow";
	}

	public SamlSession consumeAssertion(String samlResponse) {
		return consumeAssertion(samlResponse, new IdentitySettings());
	}

	public SamlSession consumeAssertion(String samlResponse, IdentitySettings settings) {
		if (samlResponse == null || samlResponse.isBlank()) {
			throw new IllegalArgumentException("SAMLResponse is required");
		}
		String decoded = new String(Base64.getDecoder().decode(samlResponse), StandardCharsets.UTF_8);
		validateSignature(decoded, settings);
		String email = extractTag(decoded, "NameID");
		if (email.isBlank()) {
			email = extractAttribute(decoded, "email");
		}
		if (email.isBlank()) {
			email = "saml-user@" + settingsDomain(decoded);
		}
		return new SamlSession(email, UUID.randomUUID().toString());
	}

	private static String extractTag(String xml, String tag) {
		String open = "<" + tag;
		int start = xml.indexOf(open);
		if (start < 0) {
			return "";
		}
		start = xml.indexOf('>', start);
		if (start < 0) {
			return "";
		}
		start += 1;
		int end = xml.indexOf("</" + tag + ">", start);
		return end < 0 ? "" : xml.substring(start, end).trim();
	}

	private static String extractAttribute(String xml, String name) {
		int index = xml.indexOf("Name=\"" + name + "\"");
		if (index < 0) {
			return "";
		}
		int valueIndex = xml.indexOf(">", index);
		if (valueIndex < 0) {
			return "";
		}
		valueIndex += 1;
		int end = xml.indexOf('<', valueIndex);
		return end < 0 ? "" : xml.substring(valueIndex, end).trim();
	}

	private static String settingsDomain(String xml) {
		return "example.com";
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static void validateSignature(String xml, IdentitySettings settings) {
		if (settings == null || settings.getSamlCertificate().isBlank()) {
			return;
		}
		if (!xml.contains("<Signature")) {
			throw new IllegalArgumentException("SAML assertion is not signed");
		}
		try {
			String certPem = settings.getSamlCertificate()
				.replace("-----BEGIN CERTIFICATE-----", "")
				.replace("-----END CERTIFICATE-----", "")
				.replaceAll("\\s", "");
			byte[] certBytes = Base64.getDecoder().decode(certPem);
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			X509Certificate certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
			PublicKey publicKey = certificate.getPublicKey();
			Matcher signatureValue = Pattern.compile("<(?:ds:)?SignatureValue[^>]*>([^<]+)</(?:ds:)?SignatureValue>").matcher(xml);
			Matcher signedInfo = Pattern.compile("<(?:ds:)?SignedInfo[\\s\\S]*?</(?:ds:)?SignedInfo>").matcher(xml);
			if (!signatureValue.find() || !signedInfo.find()) {
				throw new IllegalArgumentException("SAML signature elements are missing");
			}
			byte[] signatureBytes = Base64.getDecoder().decode(signatureValue.group(1).replaceAll("\\s", ""));
			Signature verifier = Signature.getInstance("SHA256withRSA");
			verifier.initVerify(publicKey);
			verifier.update(signedInfo.group(0).getBytes(StandardCharsets.UTF_8));
			if (!verifier.verify(signatureBytes)) {
				throw new IllegalArgumentException("SAML signature validation failed");
			}
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("SAML signature validation failed: " + ex.getMessage());
		}
	}

	public record SamlSession(String email, String sessionId) {
	}

}
