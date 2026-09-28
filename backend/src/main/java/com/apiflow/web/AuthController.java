package com.apiflow.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.apiflow.model.IdentitySettings;
import com.apiflow.service.SamlService;
import com.apiflow.service.ScimService;
import com.apiflow.service.WorkspaceService;

@RestController
public class AuthController {

	private final WorkspaceService workspaceService;
	private final SamlService samlService;
	private final ScimService scimService;

	public AuthController(WorkspaceService workspaceService, SamlService samlService, ScimService scimService) {
		this.workspaceService = workspaceService;
		this.samlService = samlService;
		this.scimService = scimService;
	}

	@GetMapping(value = "/api/auth/saml/metadata", produces = MediaType.APPLICATION_XML_VALUE)
	public String samlMetadata() {
		return samlService.metadata(identity());
	}

	@GetMapping("/api/auth/saml/login")
	public SamlLogin samlLogin() {
		return new SamlLogin(samlService.loginRedirect(identity()));
	}

	@PostMapping("/api/auth/saml/acs")
	public SamlService.SamlSession samlAcs(@RequestParam(required = false) String SAMLResponse) {
		return samlService.consumeAssertion(SAMLResponse, identity());
	}

	@GetMapping(value = "/scim/v2/Users", produces = MediaType.APPLICATION_JSON_VALUE)
	public String scimUsers(@RequestHeader(value = "Authorization", required = false) String authorization) {
		assertScim(authorization);
		return scimService.listUsers();
	}

	@PostMapping(value = "/scim/v2/Users", produces = MediaType.APPLICATION_JSON_VALUE)
	public String scimCreateUser(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody String body) {
		assertScim(authorization);
		return scimService.createUser(body);
	}

	@PatchMapping(value = "/scim/v2/Users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	public String scimPatchUser(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String id, @RequestBody String body) {
		assertScim(authorization);
		return scimService.patchUser(id, body);
	}

	@DeleteMapping("/scim/v2/Users/{id}")
	public void scimDeleteUser(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String id) {
		assertScim(authorization);
		scimService.deleteUser(id);
	}

	@GetMapping(value = "/scim/v2/Groups", produces = MediaType.APPLICATION_JSON_VALUE)
	public String scimGroups(@RequestHeader(value = "Authorization", required = false) String authorization) {
		assertScim(authorization);
		return scimService.listGroups();
	}

	private IdentitySettings identity() {
		var settings = workspaceService.workspace().getSettings();
		return settings == null ? new IdentitySettings() : settings.getIdentity();
	}

	private void assertScim(String authorization) {
		String token = identity().getScimToken();
		if (token.isBlank()) {
			throw new IllegalArgumentException("Configure a SCIM token in workspace settings");
		}
		if (authorization == null || !authorization.replace("Bearer ", "").trim().equals(token)) {
			throw new IllegalArgumentException("Invalid SCIM token");
		}
	}

	public record SamlLogin(String redirectUrl) {
	}

}
