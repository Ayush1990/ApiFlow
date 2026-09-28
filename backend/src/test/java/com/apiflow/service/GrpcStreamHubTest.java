package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.apiflow.model.ExecuteCommand;

class GrpcStreamHubTest {

	@Test
	void rejectsMissingGrpcService() {
		GrpcStreamHub hub = new GrpcStreamHub();
		ExecuteCommand command = new ExecuteCommand();
		assertThrows(IllegalArgumentException.class, () -> hub.open(command, "localhost:50051", "server"));
	}

}
