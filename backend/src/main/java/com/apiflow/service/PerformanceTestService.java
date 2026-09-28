package com.apiflow.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.CheckResult;
import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.ExecuteResult;
import com.apiflow.model.PerformanceRun;
import com.apiflow.model.RequestCollection;
import com.apiflow.store.FileStore;

@Service
public class PerformanceTestService {

	private final WorkspaceService workspaceService;
	private final DatasetService datasetService;
	private final FileStore store;
	private final java.util.concurrent.ConcurrentHashMap<String, PerformanceRun> live = new java.util.concurrent.ConcurrentHashMap<>();

	public PerformanceTestService(WorkspaceService workspaceService, DatasetService datasetService, FileStore store) {
		this.workspaceService = workspaceService;
		this.datasetService = datasetService;
		this.store = store;
	}

	public PerformanceRun start(String collectionId, String requestId, String environmentId, int virtualUsers, int iterations, int rampUpMs, String datasetId) {
		return execute(collectionId, requestId, environmentId, virtualUsers, iterations, rampUpMs, datasetId, true);
	}

	public PerformanceRun run(String collectionId, String requestId, String environmentId, int virtualUsers, int iterations, int rampUpMs, String datasetId) {
		return execute(collectionId, requestId, environmentId, virtualUsers, iterations, rampUpMs, datasetId, false);
	}

	private PerformanceRun execute(String collectionId, String requestId, String environmentId, int virtualUsers, int iterations, int rampUpMs, String datasetId, boolean async) {
		final int users = virtualUsers <= 0 ? 1 : Math.min(virtualUsers, 50);
		final int rounds = iterations <= 0 ? 1 : iterations;
		RequestCollection collection = workspaceService.exportCollection(collectionId);
		List<ApiRequest> requests = new ArrayList<>();
		if (requestId == null || requestId.isBlank()) {
			requests.addAll(collection.getRequests());
			requests.sort(Comparator.comparingInt(ApiRequest::getPosition));
		}
		else {
			requests.add(collection.getRequests().stream().filter(item -> item.getId().equals(requestId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Request not found")));
		}
		if (requests.isEmpty()) {
			throw new IllegalArgumentException("Collection has no requests to load test");
		}
		List<Map<String, String>> rows = datasetId == null || datasetId.isBlank() ? List.of() : datasetService.rowsAsMaps(datasetId, null);
		ExecutorService pool = Executors.newFixedThreadPool(users);
		List<Future<UserResult>> futures = new ArrayList<>();
		long started = System.currentTimeMillis();
		AtomicInteger success = new AtomicInteger();
		AtomicInteger failure = new AtomicInteger();
		AtomicInteger assertionPass = new AtomicInteger();
		AtomicInteger assertionFail = new AtomicInteger();
		List<Long> latencies = new CopyOnWriteArrayList<>();
		PerformanceRun run = new PerformanceRun();
		run.setId(UUID.randomUUID().toString());
		run.setStatus("running");
		run.setCollectionId(collectionId);
		run.setRequestId(requestId == null ? "" : requestId);
		run.setDatasetId(datasetId == null ? "" : datasetId);
		run.setVirtualUsers(users);
		run.setIterations(rounds);
		run.setRequestCount(requests.size());
		run.setStartedAt(started);
		live.put(run.getId(), run);
		List<String> failures = new CopyOnWriteArrayList<>();
		List<String> timeline = new CopyOnWriteArrayList<>();
		for (int user = 0; user < users; user++) {
			final int userIndex = user;
			futures.add(pool.submit((Callable<UserResult>) () -> {
				if (rampUpMs > 0) {
					Thread.sleep((long) userIndex * rampUpMs / Math.max(users, 1));
				}
				Map<String, String> row = rows.isEmpty() ? Map.of() : rows.get(userIndex % rows.size());
				int ok = 0;
				int fail = 0;
				for (int iteration = 0; iteration < rounds; iteration++) {
					for (ApiRequest request : requests) {
						long tick = System.currentTimeMillis();
						ExecuteCommand command = workspaceService.commandFromRequest(collection, request, environmentId, "");
						ExecuteResult result = workspaceService.execute(command, row);
						long elapsed = System.currentTimeMillis() - tick;
						latencies.add(elapsed);
						boolean checksOk = true;
						if (request.getPostResponseScript() != null && !request.getPostResponseScript().isBlank()) {
							ScriptRunner.ResponseApi responseApi = ScriptRunner.runResponse(request.getPostResponseScript(), new java.util.LinkedHashMap<>(row), result, null, collection.getId());
							checksOk = responseApi.getScriptChecks().stream().allMatch(CheckResult::isPassed);
							for (CheckResult check : responseApi.getScriptChecks()) {
								if (check.isPassed()) {
									assertionPass.incrementAndGet();
								}
								else {
									assertionFail.incrementAndGet();
								}
							}
							if (!checksOk && failures.size() < 30) {
								failures.add("VU " + userIndex + " " + request.getName() + " assertion failed");
							}
						}
						boolean passed = result.getStatus() > 0 && result.getStatus() < 400 && checksOk;
						if (timeline.size() < 80) {
							timeline.add("VU " + userIndex + " iter " + iteration + " " + request.getMethod() + " " + result.getStatus() + " " + elapsed + "ms");
						}
						if (passed) {
							ok++;
							success.incrementAndGet();
						}
						else {
							fail++;
							failure.incrementAndGet();
							if (failures.size() < 30) {
								failures.add("VU " + userIndex + " " + request.getName() + " status " + result.getStatus());
							}
						}
						run.setSuccess(success.get());
						run.setFailure(failure.get());
						run.setDurationMs(System.currentTimeMillis() - started);
						run.setFailures(new ArrayList<>(failures));
						run.setTimeline(new ArrayList<>(timeline));
						run.setAssertionPass(assertionPass.get());
						run.setAssertionFail(assertionFail.get());
					}
				}
				return new UserResult(userIndex, ok, fail);
			}));
		}
		if (async) {
			Thread worker = new Thread(() -> complete(pool, run, started, success, failure, latencies, failures, timeline, assertionPass, assertionFail), "apiflow-perf-" + run.getId());
			worker.setDaemon(true);
			worker.start();
			return run;
		}
		complete(pool, run, started, success, failure, latencies, failures, timeline, assertionPass, assertionFail);
		return run;
	}

	private void complete(ExecutorService pool, PerformanceRun run, long started, AtomicInteger success, AtomicInteger failure, List<Long> latencies, List<String> failures, List<String> timeline, AtomicInteger assertionPass, AtomicInteger assertionFail) {
		pool.shutdown();
		try {
			pool.awaitTermination(10, TimeUnit.MINUTES);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		long duration = System.currentTimeMillis() - started;
		List<Long> sorted = new ArrayList<>(latencies);
		sorted.sort(Long::compareTo);
		run.setStatus("completed");
		run.setDurationMs(duration);
		run.setSuccess(success.get());
		run.setFailure(failure.get());
		run.setP50Ms(percentile(sorted, 50));
		run.setP95Ms(percentile(sorted, 95));
		run.setP99Ms(percentile(sorted, 99));
		run.setRequestsPerSecond(duration <= 0 ? 0 : (success.get() + failure.get()) * 1000.0 / duration);
		run.setFailures(new ArrayList<>(failures));
		run.setTimeline(new ArrayList<>(timeline));
		run.setAssertionPass(assertionPass.get());
		run.setAssertionFail(assertionFail.get());
		live.put(run.getId(), run);
		store.update(data -> {
			data.getPerformanceRuns().add(0, run);
			while (data.getPerformanceRuns().size() > 40) {
				data.getPerformanceRuns().remove(data.getPerformanceRuns().size() - 1);
			}
		});
	}

	public PerformanceRun live(String id) {
		PerformanceRun current = live.get(id);
		if (current != null) {
			return current;
		}
		return find(id);
	}

	public List<PerformanceRun> history(String collectionId) {
		return store.copy().getPerformanceRuns().stream()
			.filter(item -> collectionId == null || collectionId.isBlank() || collectionId.equals(item.getCollectionId()))
			.toList();
	}

	public CompareResult compare(String leftId, String rightId) {
		PerformanceRun left = find(leftId);
		PerformanceRun right = find(rightId);
		return new CompareResult(left, right,
			right.getP50Ms() - left.getP50Ms(),
			right.getP95Ms() - left.getP95Ms(),
			right.getRequestsPerSecond() - left.getRequestsPerSecond(),
			right.getFailure() - left.getFailure());
	}

	private PerformanceRun find(String id) {
		return store.copy().getPerformanceRuns().stream().filter(item -> item.getId().equals(id)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Performance run not found"));
	}

	private static long percentile(List<Long> sorted, int pct) {
		if (sorted.isEmpty()) {
			return 0;
		}
		int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * pct / 100.0) - 1);
		return sorted.get(Math.max(0, index));
	}

	public record UserResult(int user, int passed, int failed) {
	}

	public record CompareResult(PerformanceRun left, PerformanceRun right, long p50DeltaMs, long p95DeltaMs, double rpsDelta, int failureDelta) {
	}

}
