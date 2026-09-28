package com.apiflow.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class SmtpClient {

	private SmtpClient() {
	}

	static void send(String host, String to, String subject, String body) {
		String server = host;
		int port = 25;
		int colon = host.lastIndexOf(':');
		if (colon > 0 && colon < host.length() - 1) {
			server = host.substring(0, colon);
			port = Integer.parseInt(host.substring(colon + 1));
		}
		try (Socket socket = new Socket(server, port);
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
			reader.readLine();
			command(writer, reader, "EHLO apiflow");
			command(writer, reader, "MAIL FROM:<apiflow@localhost>");
			command(writer, reader, "RCPT TO:<" + to + ">");
			command(writer, reader, "DATA");
			writer.write("Subject: " + subject.replace("\n", " ") + "\r\n");
			writer.write("To: " + to + "\r\n\r\n");
			writer.write(body.replace("\n", "\r\n"));
			writer.write("\r\n.\r\n");
			writer.flush();
			reader.readLine();
			command(writer, reader, "QUIT");
		}
		catch (Exception ex) {
			System.out.println("[Monitor alert email failed -> " + to + "] " + ex.getMessage());
		}
	}

	private static void command(BufferedWriter writer, BufferedReader reader, String line) throws Exception {
		writer.write(line + "\r\n");
		writer.flush();
		reader.readLine();
	}

}
