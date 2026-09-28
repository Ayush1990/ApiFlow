package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.KeyValue;
import com.apiflow.model.RequestCollection;
import com.apiflow.model.TypedField;

@Service
public class CollectionTypeValidator {

	public ValidationReport validate(RequestCollection collection, ApiRequest request) {
		List<ValidationIssue> issues = new ArrayList<>();
		validateFields("param", collection.getTypedParams(), request.getParams(), issues);
		validateFields("header", collection.getTypedHeaders(), request.getHeaders(), issues);
		if (collection.getBodySchema() != null && !collection.getBodySchema().isBlank()) {
			String body = "graphql".equals(request.getBodyType()) ? request.getGraphqlQuery() : request.getBody();
			if (body == null || body.isBlank()) {
				issues.add(new ValidationIssue("body", "Body is required by collection schema"));
			}
			else if ("json".equals(request.getBodyType()) && !looksLikeJson(body)) {
				issues.add(new ValidationIssue("body", "Body must be valid JSON"));
			}
		}
		return new ValidationReport(issues.isEmpty(), issues);
	}

	private void validateFields(String scope, List<TypedField> typedFields, List<KeyValue> values, List<ValidationIssue> issues) {
		for (TypedField typed : typedFields) {
			KeyValue match = values.stream().filter(item -> typed.getKey().equals(item.getKey())).findFirst().orElse(null);
			if (typed.isRequired() && (match == null || !match.isEnabled() || match.getValue() == null || match.getValue().isBlank())) {
				issues.add(new ValidationIssue(scope, "Missing required " + scope + ": " + typed.getKey()));
				continue;
			}
			if (match == null || match.getValue() == null || match.getValue().isBlank()) {
				continue;
			}
			String dataType = typed.getDataType().toLowerCase();
			String value = match.getValue();
			switch (dataType) {
				case "number" -> {
					try {
						Double.parseDouble(value);
					}
					catch (NumberFormatException ex) {
						issues.add(new ValidationIssue(scope, typed.getKey() + " must be a number"));
					}
				}
				case "boolean" -> {
					if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
						issues.add(new ValidationIssue(scope, typed.getKey() + " must be true or false"));
					}
				}
				case "enum" -> {
					if (!typed.getEnumValues().isBlank()) {
						boolean allowed = false;
						for (String option : typed.getEnumValues().split(",")) {
							if (option.trim().equals(value)) {
								allowed = true;
								break;
							}
						}
						if (!allowed) {
							issues.add(new ValidationIssue(scope, typed.getKey() + " must be one of: " + typed.getEnumValues()));
						}
					}
				}
				default -> {
					// string ok
				}
			}
		}
	}

	private static boolean looksLikeJson(String body) {
		String trimmed = body.trim();
		return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
	}

	public record ValidationIssue(String field, String message) {
	}

	public record ValidationReport(boolean valid, List<ValidationIssue> issues) {
	}

}
