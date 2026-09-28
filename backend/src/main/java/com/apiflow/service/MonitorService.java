package com.apiflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.apiflow.model.ChangeResult;
import com.apiflow.model.Monitor;
import com.apiflow.model.MonitorRun;
import com.apiflow.model.RunReport;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

@Service
public class MonitorService {

	private final FileStore store;
	private final WorkspaceService workspaceService;
	private final ConcurrentHashMap<String, Long> lastTriggered = new ConcurrentHashMap<>();

	public MonitorService(FileStore store, WorkspaceService workspaceService) {
		this.store = store;
		this.workspaceService = workspaceService;
	}

	public List<Monitor> list() {
		return store.copy().getMonitors();
	}

	public List<MonitorRun> history(String monitorId) {
		return store.copy().getMonitorRuns().stream()
			.filter(item -> monitorId == null || monitorId.isBlank() || monitorId.equals(item.getMonitorId()))
			.sorted((left, right) -> Long.compare(right.getStartedAt(), left.getStartedAt()))
			.limit(100)
			.toList();
	}

	public ChangeResult save(Monitor monitor) {
		if (monitor.getId() == null || monitor.getId().isBlank()) {
			monitor.setId(UUID.randomUUID().toString());
		}
		String id = monitor.getId();
		Workspace workspace = store.update(data -> {
			data.getMonitors().removeIf(item -> item.getId().equals(id));
			data.getMonitors().add(monitor);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult delete(String id) {
		Workspace workspace = store.update(data -> {
			data.getMonitors().removeIf(item -> item.getId().equals(id));
			data.getMonitorRuns().removeIf(item -> id.equals(item.getMonitorId()));
		});
		return new ChangeResult(workspace, "");
	}

	public MonitorRun run(String id) {
		Monitor monitor = store.copy().getMonitors().stream().filter(item -> item.getId().equals(id)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Monitor not found"));
		long started = System.currentTimeMillis();
		RunReport report = workspaceService.runCollection(monitor.getCollectionId(), monitor.getEnvironmentId(), "", "", false, "", false, 0, java.util.List.of(), monitor.getDatasetId());
		long duration = System.currentTimeMillis() - started;
		MonitorRun run = new MonitorRun();
		run.setId(UUID.randomUUID().toString());
		run.setMonitorId(id);
		run.setStartedAt(started);
		run.setDurationMs(duration);
		run.setPassed(report.getPassed());
		run.setFailed(report.getFailed());
		run.setReportHtml(buildReportHtml(monitor, report, duration));
		String address = localAddress();
		String ranges = store.copy().getSettings().getStaticIpRanges();
		run.setRegion(monitor.getRegion());
		run.setRunnerAddress(address);
		run.setStaticRange(ranges);
		run.setAddressAllowed(ranges.isBlank() || cidrAllows(ranges, address));
		store.update(data -> {
			Monitor stored = data.getMonitors().stream().filter(item -> item.getId().equals(id)).findFirst().orElse(monitor);
			stored.setLastRunAt(started);
			stored.setLastStatus(report.getFailed() > 0 ? "failed" : "passed");
			data.getMonitorRuns().add(0, run);
			while (data.getMonitorRuns().size() > 200) {
				data.getMonitorRuns().remove(data.getMonitorRuns().size() - 1);
			}
		});
		if (report.getFailed() > 0) {
			sendAlerts(monitor, report);
		}
		return run;
	}

	public List<UptimePoint> uptime() {
		Workspace workspace = store.copy();
		List<UptimePoint> points = new java.util.ArrayList<>();
		for (Monitor monitor : workspace.getMonitors()) {
			List<MonitorRun> runs = workspace.getMonitorRuns().stream().filter(item -> monitor.getId().equals(item.getMonitorId())).toList();
			long up = runs.stream().filter(item -> item.getFailed() == 0).count();
			double percent = runs.isEmpty() ? 100 : up * 100.0 / runs.size();
			points.add(new UptimePoint(monitor.getId(), monitor.getName(), runs.size(), percent, monitor.getRegion(), monitor.getLastStatus()));
		}
		return points;
	}

	public record UptimePoint(String id, String name, int samples, double uptimePercent, String region, String lastStatus) {
	}

	public MonitorRun publishRun(String runId) {
		return publishRun(runId, "");
	}

	public MonitorRun publishRun(String runId, String baseUrl) {
		String origin = baseUrl == null || baseUrl.isBlank() ? "" : baseUrl.replaceAll("/$", "");
		String reportUrl = "/public/monitor-" + runId + ".html";
		Workspace workspace = store.update(data -> {
			MonitorRun run = data.getMonitorRuns().stream().filter(item -> item.getId().equals(runId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Monitor run not found"));
			run.setPublished(true);
			try {
				java.nio.file.Path dir = store.root().resolve("public");
				java.nio.file.Files.createDirectories(dir);
				java.nio.file.Path file = dir.resolve("monitor-" + runId + ".html");
				java.nio.file.Files.writeString(file, run.getReportHtml());
			}
			catch (java.io.IOException ex) {
				throw new IllegalArgumentException("Could not write monitor report");
			}
			run.setReportUrl((origin.isBlank() ? "" : origin) + reportUrl);
		});
		return workspace.getMonitorRuns().stream().filter(item -> item.getId().equals(runId)).findFirst().orElseThrow();
	}

	public String runReportHtml(String runId) {
		MonitorRun run = store.copy().getMonitorRuns().stream().filter(item -> item.getId().equals(runId)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Monitor run not found"));
		if (!run.isPublished()) {
			throw new IllegalArgumentException("Monitor report is not published");
		}
		return run.getReportHtml();
	}

	@Scheduled(fixedDelay = 60_000)
	public void tick() {
		long now = System.currentTimeMillis();
		for (Monitor monitor : store.copy().getMonitors()) {
			if (!monitor.isEnabled() || !"schedule".equalsIgnoreCase(monitor.getTriggerMode())) {
				continue;
			}
			if ("private".equalsIgnoreCase(monitor.getRunnerMode())) {
				continue;
			}
			long interval = scheduleMillis(monitor.getSchedule());
			Long previous = lastTriggered.get(monitor.getId());
			if (previous != null && now - previous < interval) {
				continue;
			}
			lastTriggered.put(monitor.getId(), now);
			try {
				run(monitor.getId());
			}
			catch (Exception ignored) {
				// keep scheduler alive
			}
		}
	}

	private long scheduleMillis(String schedule) {
		if (schedule == null) {
			return 300_000;
		}
		String value = schedule.toLowerCase();
		if (value.contains("1 minute")) {
			return 60_000;
		}
		if (value.contains("15 minute")) {
			return 900_000;
		}
		if (value.contains("hour")) {
			return 3_600_000;
		}
		if (value.contains("day")) {
			return 86_400_000;
		}
		return 300_000;
	}

	private void sendAlerts(Monitor monitor, RunReport report) {
		String message = "ApiFlow monitor \"" + monitor.getName() + "\" failed: " + report.getFailed() + " failures, " + report.getPassed() + " passed.";
		for (String email : monitor.getAlertEmails()) {
			if (email != null && !email.isBlank()) {
				if (monitor.getSmtpHost().isBlank()) {
					System.out.println("[Monitor alert email -> " + email + "] " + message);
				}
				else {
					SmtpClient.send(monitor.getSmtpHost(), email, monitor.getReportTitle(), message);
				}
			}
		}
		if (!monitor.getSlackWebhook().isBlank()) {
			postWebhook(monitor.getSlackWebhook(), "{\"text\":\"" + escapeJson(message) + "\"}");
		}
		if (!monitor.getPagerDutyKey().isBlank()) {
			postWebhook("https://events.pagerduty.com/v2/enqueue", """
				{"routing_key":"%s","event_action":"trigger","payload":{"summary":"%s","severity":"error","source":"apiflow-monitor"}}
				""".formatted(escapeJson(monitor.getPagerDutyKey()), escapeJson(message)));
		}
	}

	private void postWebhook(String url, String body) {
		try {
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
			client.send(request, HttpResponse.BodyHandlers.discarding());
		}
		catch (Exception ignored) {
			// best effort
		}
	}

	private String buildReportHtml(Monitor monitor, RunReport report, long duration) {
		return """
			<!doctype html><html><head><meta charset="utf-8"><title>%s report</title></head><body>
			<h1>%s</h1><p>Region: %s</p><p>Duration: %d ms</p><p>Passed: %d · Failed: %d</p>
			<p>Uptime this run: %s</p><p>Region: %s · Runner address: %s · Static ranges: %s · Allowed: %s</p></body></html>
			""".formatted(escapeHtml(monitor.getReportTitle()), escapeHtml(monitor.getReportTitle()), escapeHtml(monitor.getRegion()), duration, report.getPassed(), report.getFailed(), report.getFailed() == 0 ? "up" : "degraded", escapeHtml(monitor.getRegion()), escapeHtml(localAddress()), escapeHtml(store.copy().getSettings().getStaticIpRanges()), cidrAllows(store.copy().getSettings().getStaticIpRanges(), localAddress()) ? "yes" : "no");
	}

	private static String localAddress() {
		try {
			java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				java.net.NetworkInterface network = interfaces.nextElement();
				if (!network.isUp() || network.isLoopback()) {
					continue;
				}
				java.util.Enumeration<java.net.InetAddress> addresses = network.getInetAddresses();
				while (addresses.hasMoreElements()) {
					java.net.InetAddress address = addresses.nextElement();
					if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()) {
						return address.getHostAddress();
					}
				}
			}
		}
		catch (Exception ignored) {
			return "";
		}
		return "127.0.0.1";
	}

	static boolean cidrAllows(String ranges, String address) {
		if (ranges == null || ranges.isBlank()) {
			return true;
		}
		if (address == null || address.isBlank()) {
			return false;
		}
		long ip = ipv4(address);
		if (ip < 0) {
			return false;
		}
		for (String range : ranges.split("[,\\s]+")) {
			String value = range.trim();
			if (value.isBlank()) {
				continue;
			}
			int slash = value.indexOf('/');
			String base = slash < 0 ? value : value.substring(0, slash);
			int bits = slash < 0 ? 32 : Integer.parseInt(value.substring(slash + 1));
			long network = ipv4(base);
			if (network < 0) {
				continue;
			}
			long mask = bits <= 0 ? 0 : bits >= 32 ? 0xffffffffL : (0xffffffffL << (32 - bits)) & 0xffffffffL;
			if ((ip & mask) == (network & mask)) {
				return true;
			}
		}
		return false;
	}

	private static long ipv4(String address) {
		String[] parts = address.split("\\.");
		if (parts.length != 4) {
			return -1;
		}
		long value = 0;
		for (String part : parts) {
			int octet = Integer.parseInt(part);
			if (octet < 0 || octet > 255) {
				return -1;
			}
			value = (value << 8) + octet;
		}
		return value;
	}

	private static String escapeHtml(String value) {
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;");
	}

	private static String escapeJson(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
