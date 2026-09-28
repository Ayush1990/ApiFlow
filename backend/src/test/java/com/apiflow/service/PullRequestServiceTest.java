package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class PullRequestServiceTest {

	@Test
	void parsesBitbucketRemote() throws Exception {
		PullRequestService service = new PullRequestService();
		Method parse = PullRequestService.class.getDeclaredMethod("parse", String.class);
		parse.setAccessible(true);
		PullRequestService.Repo repo = (PullRequestService.Repo) parse.invoke(service, "https://bitbucket.org/acme/api-tests.git");
		assertNotNull(repo);
		assertEquals("bitbucket", repo.provider());
		assertEquals("acme", repo.owner());
		assertEquals("api-tests", repo.name());
	}

}
