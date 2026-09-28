package com.apiflow.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.stereotype.Service;

import com.apiflow.model.ChangeResult;
import com.apiflow.model.Dataset;
import com.apiflow.model.Workspace;
import com.apiflow.store.FileStore;

@Service
public class DatasetService {

	private final FileStore store;

	public DatasetService(FileStore store) {
		this.store = store;
	}

	public List<Dataset> list() {
		return store.copy().getDatasets();
	}

	public ChangeResult save(Dataset dataset) {
		if (dataset.getId() == null || dataset.getId().isBlank()) {
			dataset.setId(UUID.randomUUID().toString());
		}
		String id = dataset.getId();
		Workspace workspace = store.update(data -> {
			data.getDatasets().removeIf(item -> item.getId().equals(id));
			data.getDatasets().add(dataset);
		});
		return new ChangeResult(workspace, id);
	}

	public ChangeResult delete(String id) {
		Workspace workspace = store.update(data -> data.getDatasets().removeIf(item -> item.getId().equals(id)));
		return new ChangeResult(workspace, "");
	}

	public QueryResult query(String id, String sql) {
		Dataset dataset = find(id);
		if ("jdbc".equalsIgnoreCase(dataset.getSourceType())) {
			return queryJdbc(dataset, sql);
		}
		return queryEmbedded(dataset, sql);
	}

	public List<Map<String, String>> rowsAsMaps(String id, String sql) {
		QueryResult result = query(id, sql);
		List<Map<String, String>> rows = new ArrayList<>();
		for (List<String> values : result.rows()) {
			Map<String, String> map = new LinkedHashMap<>();
			for (int index = 0; index < result.columns().size() && index < values.size(); index++) {
				map.put(result.columns().get(index), values.get(index));
			}
			rows.add(map);
		}
		return rows;
	}

	public Map<String, String> row(String id, int rowIndex) {
		QueryResult result = query(id, dataset(id).getSqlView());
		if (rowIndex < 0 || rowIndex >= result.rows().size()) {
			return Map.of();
		}
		List<String> columns = result.columns();
		List<String> values = result.rows().get(rowIndex);
		Map<String, String> map = new LinkedHashMap<>();
		for (int index = 0; index < columns.size() && index < values.size(); index++) {
			map.put(columns.get(index), values.get(index));
		}
		return map;
	}

	public String bodyFromRow(String id, int rowIndex, String template) {
		Map<String, String> vars = row(id, rowIndex);
		String body = template == null ? "{}" : template;
		for (Map.Entry<String, String> entry : vars.entrySet()) {
			body = body.replace("{{" + entry.getKey() + "}}", entry.getValue());
		}
		return body;
	}

	private Dataset find(String id) {
		Dataset dataset = dataset(id);
		if (dataset == null) {
			throw new IllegalArgumentException("Dataset not found");
		}
		return dataset;
	}

	private Dataset dataset(String id) {
		return store.copy().getDatasets().stream().filter(item -> item.getId().equals(id)).findFirst().orElse(null);
	}

	private QueryResult queryEmbedded(Dataset dataset, String sql) {
		JdbcDataSource source = new JdbcDataSource();
		source.setURL("jdbc:h2:mem:dataset-" + dataset.getId() + ";DB_CLOSE_DELAY=-1");
		try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
			loadEmbeddedTable(connection, dataset);
			String query = sql == null || sql.isBlank() ? dataset.getSqlView() : sql;
			try (ResultSet resultSet = statement.executeQuery(query)) {
				return toResult(resultSet);
			}
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Dataset query failed: " + ex.getMessage());
		}
	}

	private QueryResult queryJdbc(Dataset dataset, String sql) {
		if (dataset.getJdbcUrl().isBlank()) {
			throw new IllegalArgumentException("JDBC URL is required");
		}
		try (Connection connection = DriverManager.getConnection(dataset.getJdbcUrl(), dataset.getJdbcUser(), dataset.getJdbcPassword());
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(sql == null || sql.isBlank() ? dataset.getSqlView() : sql)) {
			return toResult(resultSet);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("JDBC query failed: " + ex.getMessage());
		}
	}

	private void loadEmbeddedTable(Connection connection, Dataset dataset) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS data");
			if ("json".equalsIgnoreCase(dataset.getSourceType())) {
				loadJson(connection, dataset.getContent());
				return;
			}
			loadCsv(connection, dataset.getContent());
		}
	}

	private void loadCsv(Connection connection, String content) throws Exception {
		String[] lines = (content == null ? "" : content).split("\\R");
		if (lines.length == 0 || lines[0].isBlank()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE data (col1 VARCHAR)");
			}
			return;
		}
		String[] headers = splitCsvLine(lines[0]);
		StringBuilder ddl = new StringBuilder("CREATE TABLE data (");
		for (int index = 0; index < headers.length; index++) {
			if (index > 0) {
				ddl.append(", ");
			}
			ddl.append(safeColumn(headers[index])).append(" VARCHAR");
		}
		ddl.append(")");
		try (Statement statement = connection.createStatement()) {
			statement.execute(ddl.toString());
			for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
				if (lines[lineIndex].isBlank()) {
					continue;
				}
				String[] values = splitCsvLine(lines[lineIndex]);
				StringBuilder insert = new StringBuilder("INSERT INTO data VALUES (");
				for (int index = 0; index < headers.length; index++) {
					if (index > 0) {
						insert.append(", ");
					}
					String value = index < values.length ? values[index] : "";
					insert.append("'").append(value.replace("'", "''")).append("'");
				}
				insert.append(")");
				statement.execute(insert.toString());
			}
		}
	}

	private void loadJson(Connection connection, String content) throws Exception {
		tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
		List<Map<String, Object>> rows = mapper.readValue(content == null || content.isBlank() ? "[]" : content,
			mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
		if (rows.isEmpty()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE data (col1 VARCHAR)");
			}
			return;
		}
		List<String> columns = new ArrayList<>(rows.get(0).keySet());
		StringBuilder ddl = new StringBuilder("CREATE TABLE data (");
		for (int index = 0; index < columns.size(); index++) {
			if (index > 0) {
				ddl.append(", ");
			}
			ddl.append(safeColumn(columns.get(index))).append(" VARCHAR");
		}
		ddl.append(")");
		try (Statement statement = connection.createStatement()) {
			statement.execute(ddl.toString());
			for (Map<String, Object> row : rows) {
				StringBuilder insert = new StringBuilder("INSERT INTO data VALUES (");
				for (int index = 0; index < columns.size(); index++) {
					if (index > 0) {
						insert.append(", ");
					}
					Object value = row.get(columns.get(index));
					insert.append("'").append(String.valueOf(value).replace("'", "''")).append("'");
				}
				insert.append(")");
				statement.execute(insert.toString());
			}
		}
	}

	private QueryResult toResult(ResultSet resultSet) throws Exception {
		ResultSetMetaData meta = resultSet.getMetaData();
		List<String> columns = new ArrayList<>();
		for (int index = 1; index <= meta.getColumnCount(); index++) {
			columns.add(meta.getColumnLabel(index));
		}
		List<List<String>> rows = new ArrayList<>();
		while (resultSet.next()) {
			List<String> row = new ArrayList<>();
			for (int index = 1; index <= meta.getColumnCount(); index++) {
				row.add(resultSet.getString(index));
			}
			rows.add(row);
		}
		return new QueryResult(columns, rows);
	}

	private static String[] splitCsvLine(String line) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char ch = line.charAt(index);
			if (ch == '"') {
				quoted = !quoted;
				continue;
			}
			if (ch == ',' && !quoted) {
				parts.add(current.toString().trim());
				current.setLength(0);
				continue;
			}
			current.append(ch);
		}
		parts.add(current.toString().trim());
		return parts.toArray(String[]::new);
	}

	private static String safeColumn(String name) {
		String value = name == null ? "col" : name.replaceAll("[^A-Za-z0-9_]", "_");
		if (value.isBlank() || Character.isDigit(value.charAt(0))) {
			return "c_" + value;
		}
		return value;
	}

	public record QueryResult(List<String> columns, List<List<String>> rows) {
	}

}
