package com.apiflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.apiflow.model.WorkspaceSettings;

class AiServiceTest {

	@Test
	void requiresPrompt() {
		AiService ai = new AiService(new AiConversationStore());
		assertThrows(IllegalArgumentException.class, () -> ai.chat(new WorkspaceSettings(), "", "", "assistant"));
	}

	@Test
	void storesConversationHistory() {
		AiConversationStore store = new AiConversationStore();
		String session = store.ensureSession(null);
		store.append(session, "user", "hello");
		store.append(session, "assistant", "hi");
		assertEquals(2, store.history(session).size());
	}

}
