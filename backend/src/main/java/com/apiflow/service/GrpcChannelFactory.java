package com.apiflow.service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Base64;

import javax.net.ssl.SSLContext;

import com.apiflow.model.ExecuteCommand;
import com.apiflow.model.RequestExtras;

import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.JdkSslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.MetadataUtils;

public final class GrpcChannelFactory {

	private GrpcChannelFactory() {
	}

	public static Channel open(String url, ExecuteCommand command) {
		return open(url, command == null ? null : command.getExtras(), command == null ? null : GrpcMetadata.fromHeaders(command.getHeaders()));
	}

	public static Channel open(String url, RequestExtras extras, Metadata metadata) {
		URI uri = URI.create(GrpcExecutor.normalizeUrl(url));
		int port = uri.getPort() > 0 ? uri.getPort() : (GrpcExecutor.isTls(url) ? 443 : 80);
		NettyChannelBuilder builder = NettyChannelBuilder.forAddress(uri.getHost(), port);
		if (GrpcExecutor.isTls(url)) {
			SslContext sslContext = nettySslContext(extras);
			if (sslContext != null) {
				builder.sslContext(sslContext);
			}
			else {
				builder.useTransportSecurity();
			}
		}
		else {
			builder.usePlaintext();
		}
		ManagedChannel channel = builder.build();
		if (metadata == null || metadata.keys().isEmpty()) {
			return channel;
		}
		return ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata));
	}

	static void shutdown(Channel channel) {
		if (channel instanceof ManagedChannel managed) {
			managed.shutdownNow();
		}
	}

	static SslContext nettySslContext(RequestExtras extras) {
		if (extras == null) {
			return null;
		}
		if (extras.getClientCertBase64().isBlank() && extras.getCaCertBase64().isBlank()) {
			return null;
		}
		try {
			SSLContext jdk = ClientCerts.context(extras);
			if (jdk != null) {
				return new JdkSslContext(jdk, true, ClientAuth.NONE);
			}
			if (!extras.getCaCertBase64().isBlank()) {
				return GrpcSslContexts.forClient()
					.trustManager(new ByteArrayInputStream(decode(extras.getCaCertBase64())))
					.build();
			}
			return null;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Could not configure gRPC mTLS: " + ex.getMessage());
		}
	}

	private static byte[] decode(String value) {
		return Base64.getDecoder().decode(value.replaceAll("\\s", ""));
	}

}
