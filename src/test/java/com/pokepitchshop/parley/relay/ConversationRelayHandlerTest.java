package com.pokepitchshop.parley.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ConversationRelayHandlerTest {

	@Mock
	private WebSocketSession session;

	private ConversationRelayHandler handler;

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		handler = new ConversationRelayHandler();
	}

	@Test
	void setupStoresCallContext() throws Exception {
		when(session.getAttributes()).thenReturn(new HashMap<>());

		String payload = """
				{
				  "type": "setup",
				  "sessionId": "VX123",
				  "callSid": "CA123",
				  "from": "+15551234567"
				}
				""";

		handler.handleTextMessage(session, new TextMessage(payload));

		assertThat(session.getAttributes().get(RelaySessionAttributes.CALL_SID)).isEqualTo("CA123");
		assertThat(session.getAttributes().get(RelaySessionAttributes.FROM)).isEqualTo("+15551234567");
	}

	@Test
	void promptEchoesCompletedUtterance() throws Exception {
		when(session.getAttributes()).thenReturn(new HashMap<>(Map.of(RelaySessionAttributes.CALL_SID, "CA123")));

		String payload = """
				{
				  "type": "prompt",
				  "voicePrompt": "What are your hours?",
				  "last": true
				}
				""";

		handler.handleTextMessage(session, new TextMessage(payload));

		ArgumentCaptor<TextMessage> captor = forClass(TextMessage.class);
		verify(session).sendMessage(captor.capture());
		RelayOutboundMessage outbound = objectMapper.readValue(captor.getValue().getPayload(), RelayOutboundMessage.class);
		assertThat(outbound.type()).isEqualTo("text");
		assertThat(outbound.token()).isEqualTo("You said: What are your hours?");
		assertThat(outbound.lastToken()).isTrue();
	}

	@Test
	void partialPromptDoesNotReply() throws Exception {
		String payload = """
				{
				  "type": "prompt",
				  "voicePrompt": "What are",
				  "last": false
				}
				""";

		handler.handleTextMessage(session, new TextMessage(payload));

		verify(session, never()).sendMessage(any());
	}
}
