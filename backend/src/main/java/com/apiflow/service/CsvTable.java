package com.apiflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvTable {

	private CsvTable() {
	}

	public static List<Map<String, String>> parse(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		List<List<String>> rows = new ArrayList<>();
		for (String line : csv.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			rows.add(split(line));
		}
		if (rows.size() < 2) {
			return List.of();
		}
		List<String> header = rows.get(0);
		List<Map<String, String>> table = new ArrayList<>();
		for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
			List<String> row = rows.get(rowIndex);
			Map<String, String> values = new LinkedHashMap<>();
			for (int column = 0; column < header.size(); column++) {
				String key = header.get(column) == null ? "" : header.get(column).trim();
				if (key.isEmpty()) {
					continue;
				}
				values.put(key, column < row.size() ? row.get(column) : "");
			}
			table.add(values);
		}
		return table;
	}

	static List<String> split(String line) {
		List<String> cells = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if (quoted) {
				if (character == '"') {
					if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
						current.append('"');
						index++;
					}
					else {
						quoted = false;
					}
				}
				else {
					current.append(character);
				}
			}
			else if (character == '"') {
				quoted = true;
			}
			else if (character == ',') {
				cells.add(current.toString());
				current.setLength(0);
			}
			else {
				current.append(character);
			}
		}
		cells.add(current.toString());
		return cells;
	}

}
