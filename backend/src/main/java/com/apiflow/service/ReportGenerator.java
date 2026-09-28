package com.apiflow.service;

import java.util.List;
import java.util.regex.Pattern;

import com.apiflow.model.RunReport;

public final class ReportGenerator {

	private static final Pattern SECRET = Pattern.compile("(Bearer\\s+)[^\\s\"']+", Pattern.CASE_INSENSITIVE);

	private ReportGenerator() {
	}

	public static String html(RunReport report, String name) {
		return html(report, name, List.of());
	}

	public static String html(RunReport report, String name, List<String> secretValues) {
		StringBuilder builder = new StringBuilder();
		builder.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>").append(escape(name)).append("</title>")
			.append("<style>body{font-family:system-ui,sans-serif;padding:24px} .ok{color:#2f855a}.err{color:#c53030} table{border-collapse:collapse;width:100%} td,th{border:1px solid #ddd;padding:8px;text-align:left}</style></head><body>");
		builder.append("<h1>").append(escape(name)).append("</h1>");
		builder.append("<p>").append(report.getPassed()).append(" passed, ").append(report.getFailed()).append(" failed</p>");
		builder.append("<table><tr><th>Result</th><th>Method</th><th>Name</th><th>Status</th></tr>");
		for (var item : report.getItems()) {
			builder.append("<tr><td class=\"").append(item.isOk() ? "ok" : "err").append("\">")
				.append(item.isOk() ? "PASS" : "FAIL").append("</td><td>")
				.append(escape(mask(item.getMethod(), secretValues))).append("</td><td>")
				.append(escape(mask(item.getName(), secretValues))).append("</td><td>")
				.append(escape(mask(item.getError() == null || item.getError().isBlank() ? String.valueOf(item.getStatus()) : item.getError(), secretValues)))
				.append("</td></tr>");
		}
		builder.append("</table></body></html>");
		return builder.toString();
	}

	public static String junit(RunReport report, String name) {
		return junit(report, name, List.of());
	}

	public static String junit(RunReport report, String name, List<String> secretValues) {
		StringBuilder builder = new StringBuilder();
		builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites tests=\"")
			.append(report.getItems().size()).append("\" failures=\"").append(report.getFailed()).append("\" name=\"")
			.append(escapeXml(name)).append("\"><testsuite tests=\"").append(report.getItems().size())
			.append("\" failures=\"").append(report.getFailed()).append("\" name=\"").append(escapeXml(name)).append("\">");
		for (var item : report.getItems()) {
			String testName = mask(item.getMethod() + " " + item.getName(), secretValues);
			builder.append("<testcase classname=\"apiflow\" name=\"").append(escapeXml(testName)).append("\"");
			if (!item.isOk()) {
				builder.append("><failure message=\"").append(escapeXml(mask(item.getError() == null ? String.valueOf(item.getStatus()) : item.getError(), secretValues)))
					.append("\"></failure></testcase>");
			}
			else {
				builder.append(" />");
			}
		}
		builder.append("</testsuite></testsuites>");
		return builder.toString();
	}

	private static String mask(String value, List<String> secrets) {
		if (value == null) {
			return "";
		}
		String masked = SECRET.matcher(value).replaceAll("$1••••");
		if (secrets != null) {
			for (String secret : secrets) {
				if (secret != null && !secret.isBlank() && secret.length() > 3) {
					masked = masked.replace(secret, "••••");
				}
			}
		}
		return masked;
	}

	private static String escape(String value) {
		return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String escapeXml(String value) {
		return escape(value).replace("\"", "&quot;");
	}

}
