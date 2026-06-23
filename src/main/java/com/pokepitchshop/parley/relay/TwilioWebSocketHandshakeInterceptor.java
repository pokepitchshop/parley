package com.pokepitchshop.parley.relay;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.pokepitchshop.parley.twilio.TwilioSignatureValidator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TwilioWebSocketHandshakeInterceptor implements HandshakeInterceptor {

	private final TwilioSignatureValidator signatureValidator;

	public TwilioWebSocketHandshakeInterceptor(TwilioSignatureValidator signatureValidator) {
		this.signatureValidator = signatureValidator;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		if (!signatureValidator.validateWebSocketUpgrade(request.getURI(), request.getHeaders())) {
			response.setStatusCode(HttpStatus.FORBIDDEN);
			return false;
		}
		return true;
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception) {
		// no-op
	}
}
