package com.apiflow.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.apiflow.model.ChangeResult;
import com.apiflow.model.Comment;
import com.apiflow.model.InventoryApp;
import com.apiflow.model.InventoryApp.CapturedCall;
import com.apiflow.model.RunReport;
import com.apiflow.model.Webhook;
import com.apiflow.model.WorkspaceMember;
import com.apiflow.service.CollaborationService;
import com.apiflow.service.CollaborationService.SdkResult;

@RestController
public class CollaborationController {

	private final CollaborationService collaborationService;

	public CollaborationController(CollaborationService collaborationService) {
		this.collaborationService = collaborationService;
	}

	@GetMapping("/api/platform/comments")
	public List<Comment> comments(@RequestParam(required = false) String targetId) {
		return collaborationService.comments(targetId);
	}

	@PostMapping("/api/platform/comments")
	public ChangeResult addComment(@RequestBody Comment comment) {
		return collaborationService.addComment(comment);
	}

	@GetMapping("/api/platform/members")
	public List<WorkspaceMember> members() {
		return collaborationService.members();
	}

	@PostMapping("/api/platform/members")
	public ChangeResult saveMember(@RequestBody WorkspaceMember member) {
		return collaborationService.saveMember(member);
	}

	@GetMapping("/api/platform/webhooks")
	public List<Webhook> webhooks() {
		return collaborationService.webhooks();
	}

	@PostMapping("/api/platform/webhooks")
	public ChangeResult saveWebhook(@RequestBody Webhook webhook) {
		return collaborationService.saveWebhook(webhook);
	}

	@PostMapping("/hooks/{id}")
	public Object trigger(@PathVariable String id) {
		return collaborationService.triggerWebhook(id);
	}

	@PostMapping("/api/platform/inventory/capture")
	public InventoryApp capture(@RequestBody CaptureBody body) {
		return collaborationService.capture(body == null ? "" : body.name(), body == null ? "" : body.environment(), body == null ? List.of() : body.calls());
	}

	@PostMapping("/api/platform/sdk")
	public SdkResult sdk(@RequestBody SdkBody body) {
		return collaborationService.generateSdk(body.collectionId(), body == null ? "typescript" : body.language());
	}

	@GetMapping("/api/platform/runs")
	public List<RunReport> runs(@RequestParam(required = false) String collectionId) {
		return collaborationService.runs(collectionId);
	}

	public record CaptureBody(String name, String environment, List<CapturedCall> calls) {
	}

	public record SdkBody(String collectionId, String language) {
	}

}
