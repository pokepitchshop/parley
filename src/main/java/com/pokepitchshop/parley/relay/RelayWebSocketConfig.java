package com.pokepitchshop.parley.relay;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RelayWebSocketConfig implements WebSocketConfigurer {

	private final ConversationRelayHandler conversationRelayHandler;

	private final TwilioWebSocketHandshakeInterceptor handshakeInterceptor;

	public RelayWebSocketConfig(
			ConversationRelayHandler conversationRelayHandler,
			TwilioWebSocketHandshakeInterceptor handshakeInterceptor) {
		this.conversationRelayHandler = conversationRelayHandler;
		this.handshakeInterceptor = handshakeInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(conversationRelayHandler, ConversationRelayPaths.RELAY_PATH)
				.addInterceptors(handshakeInterceptor)
				.setAllowedOrigins("*");
	}
}
