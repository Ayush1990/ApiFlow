package com.apiflow.web;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.apiflow.service.DocsService;
import com.apiflow.service.ShareLinkService;
import com.apiflow.service.ShareLinkService.SharePayload;

@Controller
public class PublicShareController {

	private final ShareLinkService shareLinkService;
	private final DocsService docsService;

	public PublicShareController(ShareLinkService shareLinkService, DocsService docsService) {
		this.shareLinkService = shareLinkService;
		this.docsService = docsService;
	}

	@GetMapping(value = "/share/{id}", produces = MediaType.TEXT_HTML_VALUE)
	public String sharePage(@PathVariable String id) {
		SharePayload payload = shareLinkService.read(id);
		return docsService.shareHtml(payload);
	}

}
