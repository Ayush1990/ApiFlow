package com.apiflow.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.apiflow.model.ChangeResult;
import com.apiflow.model.Monitor;
import com.apiflow.model.RunReport;
import com.apiflow.service.LocalGapService;
import com.apiflow.service.ScriptLibrary;
import com.apiflow.service.VaultStore;

import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/platform")
public class GapController {

	private final LocalGapService gaps;
	private final ScriptLibrary library;

	public GapController(LocalGapService gaps, ScriptLibrary library) {
		this.gaps = gaps;
		this.library = library;
	}

	@GetMapping("/vault")
	public Map<String, Object> vault() {
		return Map.of("locked", VaultStore.isLocked(), "entries", VaultStore.names());
	}

	@PostMapping("/vault/unlock")
	public Map<String, Object> unlockVault(@RequestBody PassphraseBody body) {
		VaultStore.unlock(body == null ? "" : body.passphrase());
		return Map.of("locked", VaultStore.isLocked(), "entries", VaultStore.names());
	}

	@PostMapping("/vault")
	public Map<String, Object> saveVault(@RequestBody VaultBody body) {
		VaultStore.put(body == null ? "" : body.key(), body == null ? "" : body.value());
		return Map.of("locked", false, "entries", VaultStore.names());
	}

	@DeleteMapping("/vault/{key}")
	public Map<String, Object> deleteVault(@PathVariable String key) {
		VaultStore.delete(key);
		return Map.of("locked", false, "entries", VaultStore.names());
	}

	@PostMapping("/capture/start")
	public Map<String, Object> startCapture(@RequestBody(required = false) PortBody body) {
		int port = gaps.startCapture(body == null ? 0 : body.port());
		return Map.of("port", port, "certificate", gaps.captureCertificate());
	}

	@PostMapping("/capture/stop")
	public Map<String, Boolean> stopCapture() {
		gaps.stopCapture();
		return Map.of("running", false);
	}

	@GetMapping("/capture")
	public List<LocalGapService.CapturedCall> captured() {
		return gaps.captured();
	}

	@PostMapping("/capture/import")
	public ChangeResult importCaptured(@RequestBody CollectionBody body) {
		return gaps.importCaptured(body.collectionId());
	}

	@GetMapping("/schedules")
	public List<LocalGapService.ScheduledRun> schedules() {
		return gaps.schedules();
	}

	@PostMapping("/schedules")
	public LocalGapService.ScheduledRun saveSchedule(@RequestBody LocalGapService.ScheduledRun run) {
		return gaps.saveSchedule(run);
	}

	@PostMapping("/runners/claim")
	public Monitor claimRunner() {
		return gaps.claimPrivateRunner();
	}

	@PostMapping("/runs/debug")
	public Map<String, String> debugRun(@RequestBody RunReport report) {
		return Map.of("explanation", gaps.debugRun(report));
	}

	@PostMapping("/specs/infer")
	public ObjectNode infer(@RequestBody ExampleBody body) {
		return gaps.inferTypes(body == null ? "" : body.example());
	}

	@PostMapping("/mocks/{collectionId}/standalone")
	public Map<String, String> standaloneMock(@PathVariable String collectionId) {
		return Map.of("file", gaps.standaloneMock(collectionId));
	}

	@PostMapping("/collections/{id}/fork")
	public ChangeResult fork(@PathVariable String id) {
		return gaps.forkCollection(id);
	}

	@PostMapping("/simulate")
	public RunReport simulate(@RequestBody SimulateBody body) {
		return gaps.simulate(body.collectionId(), body.environmentId(), body.failingPrefixes());
	}

	@GetMapping("/packages")
	public List<ScriptLibrary.PackageScript> packages() {
		return library.list();
	}

	@PostMapping("/packages")
	public ChangeResult savePackage(@RequestBody ScriptLibrary.PackageScript script) {
		return library.save(script);
	}

	@GetMapping("/grpc/mock/{service}/{method}")
	public Map<String, String> grpcMock(@PathVariable String service, @PathVariable String method) {
		return Map.of("body", gaps.grpcExample(service, method));
	}

	@PostMapping("/grpc/mock")
	public Map<String, Integer> startGrpcMock(@RequestBody(required = false) PortBody body) {
		return Map.of("port", gaps.startGrpcMock(body == null ? 50051 : body.port()));
	}

	@PostMapping("/collections/{id}/model")
	public ChangeResult applyModel(@PathVariable String id, @RequestBody ExampleBody body) {
		return gaps.applyInferredModel(id, body == null ? "" : body.example());
	}

	@PostMapping("/pulls")
	public ChangeResult openPull(@RequestBody PullBody body) {
		return gaps.openPullRequest(body.sourceId(), body.targetId(), body.title());
	}

	@PostMapping("/pulls/{id}/merge")
	public ChangeResult mergePull(@PathVariable String id) {
		return gaps.mergePullRequest(id);
	}

	public record PassphraseBody(String passphrase) {
	}

	public record PullBody(String sourceId, String targetId, String title) {
	}

	public record VaultBody(String key, String value) {
	}

	public record PortBody(int port) {
	}

	public record CollectionBody(String collectionId) {
	}

	public record ExampleBody(String example) {
	}

	public record SimulateBody(String collectionId, String environmentId, List<String> failingPrefixes) {
	}

}
