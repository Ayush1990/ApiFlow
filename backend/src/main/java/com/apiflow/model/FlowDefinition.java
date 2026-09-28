package com.apiflow.model;

import java.util.ArrayList;
import java.util.List;

public class FlowDefinition {

	private String id;
	private String name;
	private List<FlowBlock> blocks = new ArrayList<>();
	private List<FlowConnection> connections = new ArrayList<>();
	private boolean deployed;
	private int deployPort;
	private String description = "";

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<FlowBlock> getBlocks() {
		if (blocks == null) {
			blocks = new ArrayList<>();
		}
		return blocks;
	}

	public void setBlocks(List<FlowBlock> blocks) {
		this.blocks = blocks;
	}

	public List<FlowConnection> getConnections() {
		if (connections == null) {
			connections = new ArrayList<>();
		}
		return connections;
	}

	public void setConnections(List<FlowConnection> connections) {
		this.connections = connections;
	}

	public boolean isDeployed() {
		return deployed;
	}

	public void setDeployed(boolean deployed) {
		this.deployed = deployed;
	}

	public int getDeployPort() {
		return deployPort;
	}

	public void setDeployPort(int deployPort) {
		this.deployPort = deployPort;
	}

	public String getDescription() {
		return description == null ? "" : description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public static class FlowBlock {
		private String id;
		private String type;
		private String label;
		private double x;
		private double y;
		private String config = "{}";

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public double getX() {
			return x;
		}

		public void setX(double x) {
			this.x = x;
		}

		public double getY() {
			return y;
		}

		public void setY(double y) {
			this.y = y;
		}

		public String getConfig() {
			return config == null ? "{}" : config;
		}

		public void setConfig(String config) {
			this.config = config;
		}
	}

	public static class FlowConnection {
		private String from;
		private String to;
		private String when = "";

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getTo() {
			return to;
		}

		public void setTo(String to) {
			this.to = to;
		}

		public String getWhen() {
			return when == null ? "" : when;
		}

		public void setWhen(String when) {
			this.when = when;
		}
	}

}
