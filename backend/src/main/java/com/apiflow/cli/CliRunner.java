package com.apiflow.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.apiflow.model.RunReport;
import com.apiflow.service.ReportGenerator;
import com.apiflow.service.WorkspaceService;

@Component
@Order
public class CliRunner implements ApplicationRunner {

	private final WorkspaceService workspaceService;

	public CliRunner(WorkspaceService workspaceService) {
		this.workspaceService = workspaceService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!args.containsOption("apiflow.collection")) {
			return;
		}
		String collection = args.getOptionValues("apiflow.collection").get(0);
		String environment = option(args, "apiflow.env");
		String folder = option(args, "apiflow.folder");
		String dataCsv = option(args, "apiflow.data-csv");
		boolean stop = args.containsOption("apiflow.stop");
		boolean parallel = args.containsOption("apiflow.parallel");
		int delayMs = parseInt(option(args, "apiflow.delay-ms"));
		RunReport report = workspaceService.runNamed(collection, environment, folder, stop, dataCsv, parallel, delayMs);
		System.out.println(report.getPassed() + " passed, " + report.getFailed() + " failed");
		for (var item : report.getItems()) {
			System.out.println((item.isOk() ? "PASS" : "FAIL") + " " + item.getMethod() + " " + item.getName() + " " + (item.getError() == null || item.getError().isBlank() ? item.getStatus() : item.getError()));
		}
		String htmlOut = option(args, "apiflow.report-html");
		if (!htmlOut.isBlank()) {
			try {
				Files.writeString(Path.of(htmlOut), ReportGenerator.html(report, collection));
			}
			catch (Exception ex) {
				System.err.println("Could not write HTML report: " + ex.getMessage());
			}
		}
		String junitOut = option(args, "apiflow.report-junit");
		if (!junitOut.isBlank()) {
			try {
				Files.writeString(Path.of(junitOut), ReportGenerator.junit(report, collection));
			}
			catch (Exception ex) {
				System.err.println("Could not write JUnit report: " + ex.getMessage());
			}
		}
		System.exit(report.getFailed() > 0 ? 1 : 0);
	}

	private static String option(ApplicationArguments args, String name) {
		return args.containsOption(name) ? args.getOptionValues(name).get(0) : "";
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value.trim());
		}
		catch (Exception ex) {
			return 0;
		}
	}

}
