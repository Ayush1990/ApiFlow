package com.apiflow.model;

public class RequestExtras {

	private String oauthGrant = "client_credentials";
	private String oauthTokenUrl = "";
	private String oauthAuthUrl = "";
	private String oauthClientId = "";
	private String oauthClientSecret = "";
	private String oauthScope = "";
	private String oauthAccessToken = "";
	private String oauthRefreshToken = "";
	private long oauthExpiresAt;
	private String clientCertBase64 = "";
	private String clientKeyBase64 = "";
	private String clientCertPassword = "";
	private boolean mockEnabled;
	private int mockStatus = 200;
	private String mockBody = "";
	private String mockContentType = "application/json";
	private String mockBodyMatch = "";
	private int mockDelayMs;
	private String proxyUrl = "";
	private String caCertBase64 = "";
	private String awsAccessKey = "";
	private String awsSecretKey = "";
	private String awsRegion = "us-east-1";
	private String awsService = "execute-api";
	private String soapAction = "";
	private String grpcService = "";
	private String oauthUsername = "";
	private String oauthPassword = "";
	private String oauth1ConsumerKey = "";
	private String oauth1ConsumerSecret = "";
	private String oauth1Token = "";
	private String oauth1TokenSecret = "";
	private String edgeGridClientToken = "";
	private String edgeGridClientSecret = "";
	private String edgeGridAccessToken = "";
	private String edgeGridHost = "";
	private String wsSubprotocol = "";
	private String grpcProto = "";
	private boolean grpcStream;
	private String grpcMode = "unary";
	private String httpVersion = "http/1.1";

	public RequestExtras copy() {
		RequestExtras copy = new RequestExtras();
		copy.oauthGrant = getOauthGrant();
		copy.oauthTokenUrl = getOauthTokenUrl();
		copy.oauthAuthUrl = getOauthAuthUrl();
		copy.oauthClientId = getOauthClientId();
		copy.oauthClientSecret = getOauthClientSecret();
		copy.oauthScope = getOauthScope();
		copy.oauthAccessToken = getOauthAccessToken();
		copy.oauthRefreshToken = getOauthRefreshToken();
		copy.oauthExpiresAt = getOauthExpiresAt();
		copy.clientCertBase64 = getClientCertBase64();
		copy.clientKeyBase64 = getClientKeyBase64();
		copy.clientCertPassword = getClientCertPassword();
		copy.mockEnabled = mockEnabled;
		copy.mockStatus = getMockStatus();
		copy.mockBody = getMockBody();
		copy.mockContentType = getMockContentType();
		copy.mockBodyMatch = getMockBodyMatch();
		copy.mockDelayMs = getMockDelayMs();
		copy.proxyUrl = getProxyUrl();
		copy.caCertBase64 = getCaCertBase64();
		copy.awsAccessKey = getAwsAccessKey();
		copy.awsSecretKey = getAwsSecretKey();
		copy.awsRegion = getAwsRegion();
		copy.awsService = getAwsService();
		copy.soapAction = getSoapAction();
		copy.grpcService = getGrpcService();
		copy.oauthUsername = getOauthUsername();
		copy.oauthPassword = getOauthPassword();
		copy.oauth1ConsumerKey = getOauth1ConsumerKey();
		copy.oauth1ConsumerSecret = getOauth1ConsumerSecret();
		copy.oauth1Token = getOauth1Token();
		copy.oauth1TokenSecret = getOauth1TokenSecret();
		copy.edgeGridClientToken = getEdgeGridClientToken();
		copy.edgeGridClientSecret = getEdgeGridClientSecret();
		copy.edgeGridAccessToken = getEdgeGridAccessToken();
		copy.edgeGridHost = getEdgeGridHost();
		copy.wsSubprotocol = getWsSubprotocol();
		copy.grpcProto = getGrpcProto();
		copy.grpcStream = grpcStream;
		copy.grpcMode = getGrpcMode();
		copy.httpVersion = getHttpVersion();
		return copy;
	}

	public String getOauthGrant() {
		return oauthGrant == null || oauthGrant.isBlank() ? "client_credentials" : oauthGrant;
	}

	public void setOauthGrant(String oauthGrant) {
		this.oauthGrant = oauthGrant;
	}

	public String getOauthTokenUrl() {
		return oauthTokenUrl == null ? "" : oauthTokenUrl;
	}

	public void setOauthTokenUrl(String oauthTokenUrl) {
		this.oauthTokenUrl = oauthTokenUrl;
	}

	public String getOauthAuthUrl() {
		return oauthAuthUrl == null ? "" : oauthAuthUrl;
	}

	public void setOauthAuthUrl(String oauthAuthUrl) {
		this.oauthAuthUrl = oauthAuthUrl;
	}

	public String getOauthClientId() {
		return oauthClientId == null ? "" : oauthClientId;
	}

	public void setOauthClientId(String oauthClientId) {
		this.oauthClientId = oauthClientId;
	}

	public String getOauthClientSecret() {
		return oauthClientSecret == null ? "" : oauthClientSecret;
	}

	public void setOauthClientSecret(String oauthClientSecret) {
		this.oauthClientSecret = oauthClientSecret;
	}

	public String getOauthScope() {
		return oauthScope == null ? "" : oauthScope;
	}

	public void setOauthScope(String oauthScope) {
		this.oauthScope = oauthScope;
	}

	public String getOauthAccessToken() {
		return oauthAccessToken == null ? "" : oauthAccessToken;
	}

	public void setOauthAccessToken(String oauthAccessToken) {
		this.oauthAccessToken = oauthAccessToken;
	}

	public String getOauthRefreshToken() {
		return oauthRefreshToken == null ? "" : oauthRefreshToken;
	}

	public void setOauthRefreshToken(String oauthRefreshToken) {
		this.oauthRefreshToken = oauthRefreshToken;
	}

	public long getOauthExpiresAt() {
		return oauthExpiresAt;
	}

	public void setOauthExpiresAt(long oauthExpiresAt) {
		this.oauthExpiresAt = oauthExpiresAt;
	}

	public String getClientCertBase64() {
		return clientCertBase64 == null ? "" : clientCertBase64;
	}

	public void setClientCertBase64(String clientCertBase64) {
		this.clientCertBase64 = clientCertBase64;
	}

	public String getClientKeyBase64() {
		return clientKeyBase64 == null ? "" : clientKeyBase64;
	}

	public void setClientKeyBase64(String clientKeyBase64) {
		this.clientKeyBase64 = clientKeyBase64;
	}

	public String getClientCertPassword() {
		return clientCertPassword == null ? "" : clientCertPassword;
	}

	public void setClientCertPassword(String clientCertPassword) {
		this.clientCertPassword = clientCertPassword;
	}

	public boolean isMockEnabled() {
		return mockEnabled;
	}

	public void setMockEnabled(boolean mockEnabled) {
		this.mockEnabled = mockEnabled;
	}

	public int getMockStatus() {
		return mockStatus <= 0 ? 200 : mockStatus;
	}

	public void setMockStatus(int mockStatus) {
		this.mockStatus = mockStatus;
	}

	public String getMockBody() {
		return mockBody == null ? "" : mockBody;
	}

	public void setMockBody(String mockBody) {
		this.mockBody = mockBody;
	}

	public String getMockContentType() {
		return mockContentType == null || mockContentType.isBlank() ? "application/json" : mockContentType;
	}

	public void setMockContentType(String mockContentType) {
		this.mockContentType = mockContentType;
	}

	public String getMockBodyMatch() {
		return mockBodyMatch == null ? "" : mockBodyMatch;
	}

	public void setMockBodyMatch(String mockBodyMatch) {
		this.mockBodyMatch = mockBodyMatch;
	}

	public int getMockDelayMs() {
		return mockDelayMs < 0 ? 0 : mockDelayMs;
	}

	public void setMockDelayMs(int mockDelayMs) {
		this.mockDelayMs = mockDelayMs;
	}

	public String getProxyUrl() {
		return proxyUrl == null ? "" : proxyUrl;
	}

	public void setProxyUrl(String proxyUrl) {
		this.proxyUrl = proxyUrl;
	}

	public String getCaCertBase64() {
		return caCertBase64 == null ? "" : caCertBase64;
	}

	public void setCaCertBase64(String caCertBase64) {
		this.caCertBase64 = caCertBase64;
	}

	public String getAwsAccessKey() {
		return awsAccessKey == null ? "" : awsAccessKey;
	}

	public void setAwsAccessKey(String awsAccessKey) {
		this.awsAccessKey = awsAccessKey;
	}

	public String getAwsSecretKey() {
		return awsSecretKey == null ? "" : awsSecretKey;
	}

	public void setAwsSecretKey(String awsSecretKey) {
		this.awsSecretKey = awsSecretKey;
	}

	public String getAwsRegion() {
		return awsRegion == null || awsRegion.isBlank() ? "us-east-1" : awsRegion;
	}

	public void setAwsRegion(String awsRegion) {
		this.awsRegion = awsRegion;
	}

	public String getAwsService() {
		return awsService == null || awsService.isBlank() ? "execute-api" : awsService;
	}

	public void setAwsService(String awsService) {
		this.awsService = awsService;
	}

	public String getSoapAction() {
		return soapAction == null ? "" : soapAction;
	}

	public void setSoapAction(String soapAction) {
		this.soapAction = soapAction;
	}

	public String getGrpcService() {
		return grpcService == null ? "" : grpcService;
	}

	public void setGrpcService(String grpcService) {
		this.grpcService = grpcService;
	}

	public String getOauthUsername() {
		return oauthUsername == null ? "" : oauthUsername;
	}

	public void setOauthUsername(String oauthUsername) {
		this.oauthUsername = oauthUsername;
	}

	public String getOauthPassword() {
		return oauthPassword == null ? "" : oauthPassword;
	}

	public void setOauthPassword(String oauthPassword) {
		this.oauthPassword = oauthPassword;
	}

	public String getOauth1ConsumerKey() {
		return oauth1ConsumerKey == null ? "" : oauth1ConsumerKey;
	}

	public void setOauth1ConsumerKey(String oauth1ConsumerKey) {
		this.oauth1ConsumerKey = oauth1ConsumerKey;
	}

	public String getOauth1ConsumerSecret() {
		return oauth1ConsumerSecret == null ? "" : oauth1ConsumerSecret;
	}

	public void setOauth1ConsumerSecret(String oauth1ConsumerSecret) {
		this.oauth1ConsumerSecret = oauth1ConsumerSecret;
	}

	public String getOauth1Token() {
		return oauth1Token == null ? "" : oauth1Token;
	}

	public void setOauth1Token(String oauth1Token) {
		this.oauth1Token = oauth1Token;
	}

	public String getOauth1TokenSecret() {
		return oauth1TokenSecret == null ? "" : oauth1TokenSecret;
	}

	public void setOauth1TokenSecret(String oauth1TokenSecret) {
		this.oauth1TokenSecret = oauth1TokenSecret;
	}

	public String getEdgeGridClientToken() {
		return edgeGridClientToken == null ? "" : edgeGridClientToken;
	}

	public void setEdgeGridClientToken(String edgeGridClientToken) {
		this.edgeGridClientToken = edgeGridClientToken;
	}

	public String getEdgeGridClientSecret() {
		return edgeGridClientSecret == null ? "" : edgeGridClientSecret;
	}

	public void setEdgeGridClientSecret(String edgeGridClientSecret) {
		this.edgeGridClientSecret = edgeGridClientSecret;
	}

	public String getEdgeGridAccessToken() {
		return edgeGridAccessToken == null ? "" : edgeGridAccessToken;
	}

	public void setEdgeGridAccessToken(String edgeGridAccessToken) {
		this.edgeGridAccessToken = edgeGridAccessToken;
	}

	public String getEdgeGridHost() {
		return edgeGridHost == null ? "" : edgeGridHost;
	}

	public void setEdgeGridHost(String edgeGridHost) {
		this.edgeGridHost = edgeGridHost;
	}

	public String getWsSubprotocol() {
		return wsSubprotocol == null ? "" : wsSubprotocol;
	}

	public void setWsSubprotocol(String wsSubprotocol) {
		this.wsSubprotocol = wsSubprotocol;
	}

	public String getGrpcProto() {
		return grpcProto == null ? "" : grpcProto;
	}

	public void setGrpcProto(String grpcProto) {
		this.grpcProto = grpcProto;
	}

	public boolean isGrpcStream() {
		return grpcStream || "server".equalsIgnoreCase(getGrpcMode());
	}

	public void setGrpcStream(boolean grpcStream) {
		this.grpcStream = grpcStream;
	}

	public String getGrpcMode() {
		if (grpcMode == null || grpcMode.isBlank()) {
			return grpcStream ? "server" : "unary";
		}
		return grpcMode;
	}

	public void setGrpcMode(String grpcMode) {
		this.grpcMode = grpcMode;
	}

	public String getHttpVersion() {
		return httpVersion == null || httpVersion.isBlank() ? "http/1.1" : httpVersion;
	}

	public void setHttpVersion(String httpVersion) {
		this.httpVersion = httpVersion;
	}

}
