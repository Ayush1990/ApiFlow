package com.apiflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.apiflow.model.ApiRequest;
import com.apiflow.model.RequestCollection;

public final class WsdlImporter {

	private static final Pattern OPERATION = Pattern.compile("<(?:\\w+:)?operation\\s+name\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
	private static final Pattern LOCATION = Pattern.compile("<(?:\\w+:)?address\\s+location\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

	private WsdlImporter() {
	}

	public static RequestCollection importWsdl(String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("WSDL content is empty");
		}
		RequestCollection collection = new RequestCollection();
		collection.setId(UUID.randomUUID().toString());
		collection.setName("Imported WSDL");
		String baseUrl = "";
		Matcher location = LOCATION.matcher(content);
		if (location.find()) {
			baseUrl = location.group(1);
			collection.getVariables().add(new com.apiflow.model.KeyValue("baseUrl", baseUrl, true));
		}
		int position = 0;
		Matcher operation = OPERATION.matcher(content);
		while (operation.find()) {
			ApiRequest request = new ApiRequest();
			request.setId(UUID.randomUUID().toString());
			request.setName(operation.group(1));
			request.setMethod("SOAP");
			request.setUrl(baseUrl.isBlank() ? "{{baseUrl}}" : baseUrl);
			request.setBodyType("soap");
			request.setBody("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body><" + operation.group(1) + "/></soapenv:Body></soapenv:Envelope>");
			request.setPosition(position++);
			collection.getRequests().add(request);
		}
		if (collection.getRequests().isEmpty()) {
			throw new IllegalArgumentException("No WSDL operations found");
		}
		return collection;
	}

}
