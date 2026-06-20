package com.pokepitchshop.parley.transcript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class CallSummaryServiceTest {

	private static final String CALL_SID = "CA1234567890abcdef";

	@Mock
	private TranscriptService transcriptService;

	@Mock
	private ChatClient summaryChatClient;

	@Mock
	private com.pokepitchshop.parley.caller.CallerService callerService;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec responseSpec;

	private CallSummaryService callSummaryService;

	@BeforeEach
	void setUp() {
		callSummaryService = new CallSummaryService(transcriptService, summaryChatClient, callerService);
	}

	@Test
	void onCallCompletedGeneratesAndStoresSummary() {
		Transcript transcript = new Transcript(CALL_SID, "+15551234567", Instant.now());
		transcript.getTurns().add(new Turn(Instant.now(), "What are your hours?", "We are open until six."));

		given(transcriptService.findByCallSid(CALL_SID)).willReturn(Optional.of(transcript));
		given(summaryChatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(responseSpec);
		given(responseSpec.content()).willReturn("Caller asked about hours. Agent confirmed open until six.");

		callSummaryService.onCallCompleted(CALL_SID);

		verify(transcriptService).markCompleted(CALL_SID);
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(requestSpec).user(promptCaptor.capture());
		assertThat(promptCaptor.getValue())
				.contains("Caller: What are your hours?")
				.contains("Agent: We are open until six.");
		verify(transcriptService).saveSummary(CALL_SID, "Caller asked about hours. Agent confirmed open until six.");
		verify(callerService).updateAfterCall(transcript, "Caller asked about hours. Agent confirmed open until six.");
	}

	@Test
	void onCallCompletedSkipsWhenSummaryAlreadyExists() {
		Transcript transcript = new Transcript(CALL_SID, "+15551234567", Instant.now());
		transcript.getTurns().add(new Turn(Instant.now(), "Hi", "Hello"));
		transcript.setSummary("Already done.");

		given(transcriptService.findByCallSid(CALL_SID)).willReturn(Optional.of(transcript));

		callSummaryService.onCallCompleted(CALL_SID);

		verify(transcriptService).markCompleted(CALL_SID);
		verify(summaryChatClient, never()).prompt();
		verify(transcriptService, never()).saveSummary(eq(CALL_SID), any());
	}

	@Test
	void formatTranscriptJoinsTurns() {
		Transcript transcript = new Transcript(CALL_SID, "+15551234567", Instant.now());
		transcript.setTurns(List.of(
				new Turn(Instant.now(), "First question", "First answer"),
				new Turn(Instant.now(), "Second question", "Second answer")));

		String formatted = CallSummaryService.formatTranscript(transcript);

		assertThat(formatted).isEqualTo("""
				Caller: First question
				Agent: First answer

				Caller: Second question
				Agent: Second answer""");
	}

}
