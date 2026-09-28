package com.apiflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;

import com.apiflow.model.KeyValue;

@Service
public class GraphqlIntrospection {

	private static final String QUERY = """
			{"query":"query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { kind name fields(includeDeprecated:true) { name description args { name type { name kind ofType { name kind } } defaultValue } type { name kind ofType { name kind ofType { name kind } } } } } } }"}
			""";

	public String introspect(String url, List<KeyValue> headers) {
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(QUERY));
			if (headers != null) {
				for (KeyValue header : headers) {
					if (header != null && header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
						builder.header(header.getKey().trim(), header.getValue() == null ? "" : header.getValue());
					}
				}
			}
			HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("Introspection returned " + response.statusCode());
			}
			return response.body();
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not introspect GraphQL schema: " + ex.getMessage());
		}
	}

}
