package com.apiflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;
import org.springframework.stereotype.Component;

import com.apiflow.store.FileStore;

@Component
public class ScriptDatasets {

	private static volatile ScriptDatasets instance;

	private final FileStore store;
	private final DatasetService datasetService;

	public ScriptDatasets(FileStore store, DatasetService datasetService) {
		this.store = store;
		this.datasetService = datasetService;
		instance = this;
	}

	public static DatasetsApi api() {
		if (instance == null) {
			return new DatasetsApi(null, null);
		}
		return new DatasetsApi(instance.store, instance.datasetService);
	}

	public static final class DatasetsApi {

		private final FileStore store;
		private final DatasetService datasetService;

		DatasetsApi(FileStore store, DatasetService datasetService) {
			this.store = store;
			this.datasetService = datasetService;
		}

		@HostAccess.Export
		public List<Map<String, String>> get(String datasetId) {
			if (datasetId == null || datasetId.isBlank() || datasetService == null) {
				return List.of();
			}
			return datasetService.rowsAsMaps(datasetId, null);
		}

		@HostAccess.Export
		public List<Map<String, String>> query(String datasetId, String sql) {
			if (datasetId == null || datasetId.isBlank() || datasetService == null) {
				return List.of();
			}
			return datasetService.rowsAsMaps(datasetId, sql);
		}

		@HostAccess.Export
		public Map<String, String> row(String datasetId, int index) {
			if (datasetId == null || datasetId.isBlank() || datasetService == null) {
				return Map.of();
			}
			return datasetService.row(datasetId, index);
		}
	}

	public static final class PostmanCompat {

		private final DatasetsApi datasets;

		public PostmanCompat(DatasetsApi datasets) {
			this.datasets = datasets;
		}

		@HostAccess.Export
		public DatasetsApi getDatasets() {
			return datasets;
		}
	}

}
