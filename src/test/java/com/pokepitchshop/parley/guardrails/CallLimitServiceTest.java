package com.pokepitchshop.parley.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokepitchshop.parley.transcript.Transcript;
import com.pokepitchshop.parley.transcript.TranscriptService;
import com.pokepitchshop.parley.transcript.Turn;
import com.pokepitchshop.parley.voice.VoiceProperties;

@ExtendWith(MockitoExtension.class)
class CallLimitServiceTest {

	private static final String CALL_SID = "CA1234567890abcdef";

	@Mock
	private TranscriptService transcriptService;

	private VoiceProperties voiceProperties;

	private CallLimitService callLimitService;

	@BeforeEach
	void setUp() {
		voiceProperties = new VoiceProperties("POLLY_JOANNA_NEURAL", 3, 2, 1);
		callLimitService = new CallLimitService(transcriptService, voiceProperties);
	}

	@Test
	void hasReachedTurnLimitWhenTurnCountMeetsMax() {
		Transcript transcript = new Transcript(CALL_SID, "+15551234567", Instant.now());
		transcript.getTurns().add(new Turn(Instant.now(), "Hi", "Hello"));
		transcript.getTurns().add(new Turn(Instant.now(), "Hours?", "Until six."));
		given(transcriptService.findByCallSid(CALL_SID)).willReturn(Optional.of(transcript));

		assertThat(callLimitService.hasReachedTurnLimit(CALL_SID)).isTrue();
	}

	@Test
	void canInvokeToolUntilBudgetExceeded() {
		assertThat(callLimitService.canInvokeTool(CALL_SID)).isTrue();
		callLimitService.recordToolCall(CALL_SID);
		assertThat(callLimitService.canInvokeTool(CALL_SID)).isFalse();
	}

	@Test
	void clearCallResetsToolBudget() {
		callLimitService.recordToolCall(CALL_SID);
		callLimitService.clearCall(CALL_SID);
		assertThat(callLimitService.canInvokeTool(CALL_SID)).isTrue();
	}

}
