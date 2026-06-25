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
import com.pokepitchshop.parley.voice.TurnLatencyTracker;
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

	private HashMap<String, Object> sessionAttributes;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		handler = new ConversationRelayHandler(voiceReplyService, DIRECT_EXECUTOR);
		sessionAttributes = new HashMap<>();
		when(session.getAttributes()).thenReturn(sessionAttributes);
	}

	@Test
	void setupStoresCallContext() throws Exception {
		handler.handleTextMessage(session, new TextMessage("""
				{
				  "type": "setup",
				  "sessionId": "VX123",
				  "callSid": "CA123",
				  "from": "+15551234567"
				}
				"""));

		assertThat(sessionAttributes.get(RelaySessionAttributes.CALL_SID)).isEqualTo("CA123");
		assertThat(sessionAttributes.get(RelaySessionAttributes.FROM)).isEqualTo("+15551234567");
	}

	@Test
	void promptStreamsAgentAnswerChunks() throws Exception {
		sessionAttributes.put(RelaySessionAttributes.CALL_SID, "CA123");
		sessionAttributes.put(RelaySessionAttributes.FROM, "+15551234567");
		when(session.isOpen()).thenReturn(true);
		doAnswer(invocation -> {
			VoiceReplyChunkConsumer consumer = invocation.getArgument(3);
			TurnLatencyTracker latency = invocation.getArgument(4);
			latency.markLlmFirstToken();
			latency.markLlmComplete();
			consumer.onChunk("We are open.", false);
			consumer.onChunk("We close at eight.", true);
			return null;
		}).when(voiceReplyService)
				.streamReplyToUtterance(
						eq("CA123"), eq("+15551234567"), eq("What are your hours?"), any(), any(RelayTurnLatency.class));

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
	void partialPromptStartsSttTimingWithoutReplying() throws Exception {
		handler.handleTextMessage(session, new TextMessage("""
				{
				  "type": "prompt",
				  "voicePrompt": "What are",
				  "last": false
				}
				"""));

		assertThat(sessionAttributes).containsKey(RelaySessionAttributes.STT_STARTED_AT);
		verify(voiceReplyService, never()).streamReplyToUtterance(any(), any(), any(), any(), any());
		verify(session, never()).sendMessage(any());
	}

	@Test
	void finalPromptComputesSttDurationFromPartialPrompts() throws Exception {
		sessionAttributes.put(RelaySessionAttributes.CALL_SID, "CA123");
		sessionAttributes.put(RelaySessionAttributes.FROM, "+15551234567");
		sessionAttributes.put(RelaySessionAttributes.STT_STARTED_AT, System.currentTimeMillis() - 250);
		when(session.isOpen()).thenReturn(true);
		doAnswer(invocation -> {
			invocation.getArgument(3, VoiceReplyChunkConsumer.class).onChunk("Okay.", true);
			return null;
		}).when(voiceReplyService)
				.streamReplyToUtterance(any(), any(), any(), any(), any(RelayTurnLatency.class));

		handler.handleTextMessage(session, new TextMessage("""
				{
				  "type": "prompt",
				  "voicePrompt": "What are your hours?",
				  "last": true
				}
				"""));

		ArgumentCaptor<RelayTurnLatency> latencyCaptor = forClass(RelayTurnLatency.class);
		verify(voiceReplyService).streamReplyToUtterance(
				eq("CA123"), eq("+15551234567"), eq("What are your hours?"), any(), latencyCaptor.capture());
		assertThat(sessionAttributes).doesNotContainKey(RelaySessionAttributes.STT_STARTED_AT);
	}

	@Test
	void interruptDiscardsStaleReply() throws Exception {
		sessionAttributes.put(RelaySessionAttributes.CALL_SID, "CA123");
		sessionAttributes.put(RelaySessionAttributes.FROM, "+15551234567");
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
				.streamReplyToUtterance(eq("CA123"), eq("+15551234567"), any(), any(), any());

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
