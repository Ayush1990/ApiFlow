package com.apiflow.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

@Component
public class MqttHub {

	private final ConcurrentHashMap<String, Live> sessions = new ConcurrentHashMap<>();

	public Opened connect(String broker, String topic, String username, String password, int qos) {
		if (broker == null || broker.isBlank()) {
			throw new IllegalArgumentException("MQTT broker URL is required");
		}
		try {
			String id = UUID.randomUUID().toString();
			String clientId = "apiflow-" + id.substring(0, 8);
			MqttClient client = new MqttClient(broker, clientId);
			MqttConnectOptions options = new MqttConnectOptions();
			options.setAutomaticReconnect(true);
			options.setCleanSession(true);
			if (username != null && !username.isBlank()) {
				options.setUserName(username);
			}
			if (password != null && !password.isBlank()) {
				options.setPassword(password.toCharArray());
			}
			Live live = new Live(client);
			client.setCallback(new MqttCallback() {
				@Override
				public void connectionLost(Throwable cause) {
					live.closed = true;
					live.frames.add(new Frame("status", cause == null ? "Disconnected" : cause.getMessage(), "status"));
				}

				@Override
				public void messageArrived(String arrivedTopic, MqttMessage message) {
					live.frames.add(new Frame("in", new String(message.getPayload(), StandardCharsets.UTF_8), arrivedTopic));
					trim(live);
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken token) {
					live.frames.add(new Frame("status", "Delivery complete", "status"));
				}
			});
			client.connect(options);
			if (topic != null && !topic.isBlank()) {
				client.subscribe(topic, qos <= 0 ? 1 : qos);
				live.subscribedTopic = topic;
			}
			live.frames.add(new Frame("status", "Connected to " + broker, "status"));
			sessions.put(id, live);
			return new Opened(id);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("MQTT connect failed: " + ex.getMessage());
		}
	}

	public Snapshot publish(String id, String topic, String payload, int qos, boolean retained) {
		Live live = require(id);
		try {
			MqttMessage message = new MqttMessage((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
			message.setQos(qos <= 0 ? 0 : qos);
			message.setRetained(retained);
			live.client.publish(topic, message);
			live.frames.add(new Frame("out", payload, topic));
			trim(live);
			return snapshot(live);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("MQTT publish failed: " + ex.getMessage());
		}
	}

	public Snapshot frames(String id) {
		return snapshot(require(id));
	}

	public void close(String id) {
		Live live = sessions.remove(id);
		if (live == null) {
			return;
		}
		live.closed = true;
		try {
			if (live.client.isConnected()) {
				live.client.disconnect();
			}
			live.client.close();
		}
		catch (Exception ignored) {
			// best effort
		}
	}

	private Live require(String id) {
		Live live = sessions.get(id);
		if (live == null) {
			throw new IllegalArgumentException("MQTT session closed");
		}
		return live;
	}

	private Snapshot snapshot(Live live) {
		return new Snapshot(live.closed, List.copyOf(live.frames));
	}

	private static void trim(Live live) {
		while (live.frames.size() > 200) {
			live.frames.remove(0);
		}
	}

	private static final class Live {
		private final MqttClient client;
		private volatile boolean closed;
		private String subscribedTopic = "";
		private final CopyOnWriteArrayList<Frame> frames = new CopyOnWriteArrayList<>();

		private Live(MqttClient client) {
			this.client = client;
		}
	}

	public record Opened(String id) {
	}

	public record Frame(String direction, String text, String topic) {
	}

	public record Snapshot(boolean closed, List<Frame> frames) {
	}

}
