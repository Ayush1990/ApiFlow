package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.Assertion;
import com.apiflow.model.KeyValue;

class BruAuthRoundTripTest {

	@Test
	void roundTripsOauthPasswordGrant() {
		ApiRequest request = base("OAuth Password");
		request.setAuthType("oauth2");
		request.getExtras().setOauthGrant("password");
		request.getExtras().setOauthTokenUrl("https://auth.example.com/token");
		request.getExtras().setOauthAuthUrl("https://auth.example.com/authorize");
		request.getExtras().setOauthClientId("client-id");
		request.getExtras().setOauthClientSecret("client-secret");
		request.getExtras().setOauthScope("read write");
		request.getExtras().setOauthUsername("alice");
		request.getExtras().setOauthPassword("secret");
		ApiRequest parsed = BrunoParser.request(ExportService.BrunoExporter.request(request, ""));
		assertEquals("oauth2", parsed.getAuthType());
		assertEquals("password", parsed.getExtras().getOauthGrant());
		assertEquals("https://auth.example.com/token", parsed.getExtras().getOauthTokenUrl());
		assertEquals("client-id", parsed.getExtras().getOauthClientId());
		assertEquals("alice", parsed.getExtras().getOauthUsername());
		assertEquals("secret", parsed.getExtras().getOauthPassword());
	}

	@Test
	void roundTripsAwsDigestAndNtlm() {
		ApiRequest aws = base("AWS");
		aws.setAuthType("awsv4");
		aws.getExtras().setAwsAccessKey("AKIA123");
		aws.getExtras().setAwsSecretKey("secret-key");
		aws.getExtras().setAwsRegion("eu-west-1");
		aws.getExtras().setAwsService("s3");
		ApiRequest parsedAws = BrunoParser.request(ExportService.BrunoExporter.request(aws, ""));
		assertEquals("awsv4", parsedAws.getAuthType());
		assertEquals("AKIA123", parsedAws.getExtras().getAwsAccessKey());
		assertEquals("eu-west-1", parsedAws.getExtras().getAwsRegion());

		ApiRequest digest = base("Digest");
		digest.setAuthType("digest");
		digest.setAuthUsername("user");
		digest.setAuthPassword("pass");
		ApiRequest parsedDigest = BrunoParser.request(ExportService.BrunoExporter.request(digest, ""));
		assertEquals("digest", parsedDigest.getAuthType());
		assertEquals("user", parsedDigest.getAuthUsername());

		ApiRequest ntlm = base("NTLM");
		ntlm.setAuthType("ntlm");
		ntlm.setAuthUsername("domain\\user");
		ntlm.setAuthPassword("pass");
		ApiRequest parsedNtlm = BrunoParser.request(ExportService.BrunoExporter.request(ntlm, ""));
		assertEquals("ntlm", parsedNtlm.getAuthType());
		assertEquals("domain\\user", parsedNtlm.getAuthUsername());
	}

	@Test
	void roundTripsMultipartAndAssertions() {
		ApiRequest request = base("Upload");
		request.setMethod("POST");
		request.setBodyType("multipart");
		request.getForm().add(new KeyValue("file", "hello.txt", true));
		request.getForm().add(new KeyValue("note", "sample", true));

		Assertion status = new Assertion();
		status.setType("status");
		status.setExpected("201");
		Assertion bodyContains = new Assertion();
		bodyContains.setType("body:contains");
		bodyContains.setExpected("uploaded");
		request.getAssertions().add(status);
		request.getAssertions().add(bodyContains);

		String bru = ExportService.BrunoExporter.request(request, "");
		assertTrue(bru.contains("body:multipart"));
		assertTrue(bru.contains("status: eq 201"));
		assertTrue(bru.contains("body: contains uploaded"));

		ApiRequest parsed = BrunoParser.request(bru);
		assertEquals("multipart", parsed.getBodyType());
		assertEquals(2, parsed.getForm().size());
		assertEquals("hello.txt", parsed.getForm().get(0).getValue());
		assertEquals(2, parsed.getAssertions().size());
		assertEquals("status", parsed.getAssertions().get(0).getType());
		assertEquals("201", parsed.getAssertions().get(0).getExpected());
		assertEquals("body:contains", parsed.getAssertions().get(1).getType());
		assertEquals("uploaded", parsed.getAssertions().get(1).getExpected());
	}

	@Test
	void roundTripsOauth1AndEdgeGrid() {
		ApiRequest oauth1 = base("OAuth1");
		oauth1.setAuthType("oauth1");
		oauth1.getExtras().setOauth1ConsumerKey("ck");
		oauth1.getExtras().setOauth1ConsumerSecret("cs");
		oauth1.getExtras().setOauth1Token("tok");
		oauth1.getExtras().setOauth1TokenSecret("ts");
		ApiRequest parsedOauth1 = BrunoParser.request(ExportService.BrunoExporter.request(oauth1, ""));
		assertEquals("oauth1", parsedOauth1.getAuthType());
		assertEquals("ck", parsedOauth1.getExtras().getOauth1ConsumerKey());

		ApiRequest edge = base("EdgeGrid");
		edge.setAuthType("edgegrid");
		edge.getExtras().setEdgeGridClientToken("ct");
		edge.getExtras().setEdgeGridClientSecret("cs");
		edge.getExtras().setEdgeGridAccessToken("at");
		edge.getExtras().setEdgeGridHost("example.akamai.net");
		ApiRequest parsedEdge = BrunoParser.request(ExportService.BrunoExporter.request(edge, ""));
		assertEquals("edgegrid", parsedEdge.getAuthType());
		assertEquals("example.akamai.net", parsedEdge.getExtras().getEdgeGridHost());
	}

	private static ApiRequest base(String name) {
		ApiRequest request = new ApiRequest();
		request.setName(name);
		request.setMethod("GET");
		request.setUrl("https://example.com/api");
		return request;
	}

}
