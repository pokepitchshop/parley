package com.pokepitchshop.parley.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokepitchshop.parley.voice.VoiceReplyChunkConsumer;
import com.pokepitchshop.parley.voice.VoiceReplyService;

@ExtendWith(MockitoExtension.class)
class ConversationRelayHandlerTest {

	private static final Executor DIRECT_EXECUTOR = Runnable::run;

	@Mock
	private WebSocketSession session;

	@Mock
	private VoiceReplyService voiceReplyService;

	private ConversationRelayHandler handler;

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		handler = new ConversationRelayHandler(voiceReplyService, DIRECT_EXECUTOR);
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
	void promptStreamsAgentAnswerChunks() throws Exception {
		when(session.getAttributes()).thenReturn(new HashMap<>(Map.of(
				RelaySessionAttributes.CALL_SID, "CA123",
				RelaySessionAttributes.FROM, "+15551234567")));
		when(session.isOpen()).thenReturn(true);
		doAnswer(invocation -> {
			VoiceReplyChunkConsumer consumer = invocation.getArgument(3);
			consumer.onChunk("We are open.", false);
			consumer.onChunk("We close at eight.", true);
			return null;
		}).when(voiceReplyService)
				.streamReplyToUtterance(eq("CA123"), eq("+15551234567"), eq("What are your hours?"), any());

		handler.handleTextMessage(session, new TextMessage("""
				{
				  "type": "prompt",
				  "voicePrompt": "What are your hours?",
				  "last": true
				}
				"""));

		ArgumentCaptor<TextMessage> captor = forClass(TextMessage.class);
		verify(session, org.mockito.Mockito.times(2)).sendMessage(captor.capture());
		List<RelayOutboundMessage> outbound = new ArrayList<>();
		for (TextMessage message : captor.getAllValues()) {
			outbound.add(objectMapper.readValue(message.getPayload(), RelayOutboundMessage.class));
		}
		assertThat(outbound.get(0).token()).isEqualTo("We are open.");
		assertThat(outbound.get(0).lastToken()).isFalse();
		assertThat(outbound.get(1).token()).isEqualTo("We close at eight.");
		assertThat(outbound.get(1).lastToken()).isTrue();
	}

	@Test
	void partialPromptDoesNotReply() throws Exception {
		handler.handleTextMessage(session, new TextMessage("""
				{
				  "type": "prompt",
				  "voicePrompt": "What are",
				  "last": false
				}
				"""));

		verify(voiceReplyService, never()).streamReplyToUtterance(any(), any(), any(), any());
		verify(session, never()).sendMessage(any());
	}

	@Test
	void interruptDiscardsStaleReply() throws Exception {
		when(session.getAttributes()).thenReturn(new HashMap<>(Map.of(
				RelaySessionAttributes.CALL_SID, "CA123",
				RelaySessionAttributes.FROM, "+15551234567")));
		doAnswer(invocation -> {
			VoiceReplyChunkConsumer consumer = invocation.getArgument(3);
			handler.handleTextMessage(session, new TextMessage("""
					{
					  "type": "interrupt",
					  "utteranceUntilInterrupt": "We are open",
					  "durationUntilInterruptMs": 400
					}
					"""));
			consumer.onChunk("We are open until six.", true);
			return null;
		}).when(voiceReplyService)
				.streamReplyToUtterance(eq("CA123"), eq("+15551234567"), any(), any());

		handler.handleTextMessage(session, new TextMessage("""
				{
				  "type": "prompt",
				  "voicePrompt": "What are your hours?",
				  "last": true
				}
				"""));

		verify(session, never()).sendMessage(any());
	}
}
