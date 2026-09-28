package com.apiflow.service;

import java.util.UUID;

import com.apiflow.model.ActivityEvent;
import com.apiflow.store.FileStore;

final class ActivityLog {

	private ActivityLog() {
	}

	static void add(FileStore store, String kind, String message) {
		if (store == null) {
			return;
		}
		store.update(data -> {
			ActivityEvent event = new ActivityEvent();
			event.setId(UUID.randomUUID().toString());
			event.setKind(kind == null ? "update" : kind);
			event.setMessage(message == null ? "" : message);
			event.setAt(System.currentTimeMillis());
			data.getActivity().add(0, event);
			while (data.getActivity().size() > 100) {
				data.getActivity().remove(data.getActivity().size() - 1);
			}
		});
	}

}
