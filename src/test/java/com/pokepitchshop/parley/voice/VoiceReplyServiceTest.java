package com.pokepitchshop.parley.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import com.pokepitchshop.parley.caller.CallerContext;
import com.pokepitchshop.parley.caller.CallerService;
import com.pokepitchshop.parley.guardrails.AgentGuardrails;
import com.pokepitchshop.parley.guardrails.CallLimitService;
import com.pokepitchshop.parley.guardrails.OutOfScopeDetector;
import com.pokepitchshop.parley.guardrails.ToolCallGuardrail;
import com.pokepitchshop.parley.guardrails.ToolTurnDetector;
import com.pokepitchshop.parley.transcript.TranscriptService;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class VoiceReplyServiceTest {

	private static final String CALL_SID = "CA1234567890abcdef";

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec responseSpec;

	@Mock
	private ChatClient.StreamResponseSpec streamResponseSpec;

	@Mock
	private TranscriptService transcriptService;

	@Mock
	private CallerService callerService;

	@Mock
	private CallLimitService callLimitService;

	@Mock
	private OutOfScopeDetector outOfScopeDetector;

	@Mock
	private ToolTurnDetector toolTurnDetector;

	@Mock
	private ToolCallGuardrail toolCallGuardrail;

	private VoiceReplyService service;

	@BeforeEach
	void setUp() {
		service = new VoiceReplyService(
				chatClient,
				transcriptService,
				callerService,
				callLimitService,
				outOfScopeDetector,
				toolTurnDetector,
				toolCallGuardrail);
	}

	@Test
	void replyToUtteranceUsesLlmWithMemory() {
		CallerContext context = CallerContext.anonymous();
		given(callLimitService.hasReachedTurnLimit(CALL_SID)).willReturn(false);
		given(outOfScopeDetector.cannedDecline("What are your hours?")).willReturn(Optional.empty());
		given(toolTurnDetector.looksLikeToolAction("What are your hours?")).willReturn(false);
		given(callerService.contextFor("+15551234567")).willReturn(context);
		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.system(anyString())).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.advisors(any(Consumer.class))).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(responseSpec);
		given(responseSpec.content()).willReturn("We are open until six.");

		String reply = service.replyToUtterance(CALL_SID, "+15551234567", "What are your hours?");

		assertThat(reply).isEqualTo("We are open until six.");
		verify(requestSpec).system(context.systemPromptSnippet());
		verify(transcriptService).appendTurn(CALL_SID, "+15551234567", "What are your hours?", "We are open until six.");
	}

	@Test
	void replyToUtteranceReturnsFallbackWhenLlmFails() {
		given(callLimitService.hasReachedTurnLimit(CALL_SID)).willReturn(false);
		given(outOfScopeDetector.cannedDecline("What are your hours?")).willReturn(Optional.empty());
		given(toolTurnDetector.looksLikeToolAction("What are your hours?")).willReturn(false);
		given(callerService.contextFor("+15551234567")).willReturn(CallerContext.anonymous());
		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.system(anyString())).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.advisors(any(Consumer.class))).willThrow(new RuntimeException("404 deployment not found"));

		String reply = service.replyToUtterance(CALL_SID, "+15551234567", "What are your hours?");

		assertThat(reply).contains("having trouble thinking");
		verify(transcriptService, never()).appendTurn(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void streamReplyToUtteranceEmitsSentenceChunks() {
		CallerContext context = CallerContext.anonymous();
		List<String> chunks = new ArrayList<>();
		List<Boolean> lastFlags = new ArrayList<>();
		given(callLimitService.hasReachedTurnLimit(CALL_SID)).willReturn(false);
		given(outOfScopeDetector.cannedDecline("What are your hours?")).willReturn(Optional.empty());
		given(toolTurnDetector.looksLikeToolAction("What are your hours?")).willReturn(false);
		given(callerService.contextFor("+15551234567")).willReturn(context);
		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.system(anyString())).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.advisors(any(Consumer.class))).willReturn(requestSpec);
		given(requestSpec.stream()).willReturn(streamResponseSpec);
		given(streamResponseSpec.content()).willReturn(Flux.just("We are open. ", "We close at eight."));

		service.streamReplyToUtterance(
				CALL_SID,
				"+15551234567",
				"What are your hours?",
				(text, last) -> {
					chunks.add(text);
					lastFlags.add(last);
					return true;
				});

		assertThat(chunks).containsExactly("We are open.", "We close at eight.");
		assertThat(lastFlags).containsExactly(false, true);
		verify(transcriptService).appendTurn(
				CALL_SID, "+15551234567", "What are your hours?", "We are open. We close at eight.");
	}

	@Test
	void replyToUtteranceReturnsCannedDeclineWithoutLlm() {
		given(callLimitService.hasReachedTurnLimit(CALL_SID)).willReturn(false);
		given(outOfScopeDetector.cannedDecline("What's the weather?"))
				.willReturn(Optional.of(AgentGuardrails.OFF_TOPIC_DECLINE.trim()));

		String reply = service.replyToUtterance(CALL_SID, "+15551234567", "What's the weather?");

		assertThat(reply).contains("I can't help with that");
		verify(chatClient, never()).prompt();
		verify(transcriptService).appendTurn(
				CALL_SID, "+15551234567", "What's the weather?", AgentGuardrails.OFF_TOPIC_DECLINE.trim());
	}
}
