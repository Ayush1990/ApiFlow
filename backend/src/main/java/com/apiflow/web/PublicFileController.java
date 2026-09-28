package com.apiflow.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.apiflow.service.CollaborationService;

@RestController
public class PublicFileController {

	private final CollaborationService collaborationService;

	public PublicFileController(CollaborationService collaborationService) {
		this.collaborationService = collaborationService;
	}

	@GetMapping(value = "/public/{name}", produces = MediaType.TEXT_HTML_VALUE)
	public String file(@PathVariable String name) {
		return collaborationService.readPublic(name);
	}

}
