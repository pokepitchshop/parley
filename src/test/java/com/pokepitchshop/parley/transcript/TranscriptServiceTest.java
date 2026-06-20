package com.pokepitchshop.parley.transcript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranscriptServiceTest {

	private static final String CALL_SID = "CA1234567890abcdef";

	@Mock
	private TranscriptRepository transcriptRepository;

	private TranscriptService transcriptService;

	@BeforeEach
	void setUp() {
		transcriptService = new TranscriptService(transcriptRepository);
	}

	@Test
	void appendTurnCreatesTranscriptWhenMissing() {
		given(transcriptRepository.findById(CALL_SID)).willReturn(Optional.empty());
		given(transcriptRepository.save(any(Transcript.class))).willAnswer(invocation -> invocation.getArgument(0));

		transcriptService.appendTurn(CALL_SID, "+15551234567", "Hello", "Hi there.");

		ArgumentCaptor<Transcript> captor = ArgumentCaptor.forClass(Transcript.class);
		verify(transcriptRepository).save(captor.capture());
		Transcript saved = captor.getValue();
		assertThat(saved.getCallSid()).isEqualTo(CALL_SID);
		assertThat(saved.getFromNumber()).isEqualTo("+15551234567");
		assertThat(saved.getTurns()).hasSize(1);
		assertThat(saved.getTurns().getFirst().caller()).isEqualTo("Hello");
		assertThat(saved.getTurns().getFirst().agent()).isEqualTo("Hi there.");
	}

	@Test
	void appendTurnAddsToExistingTranscript() {
		Transcript existing = new Transcript(CALL_SID, "+15551234567", Instant.now());
		existing.getTurns().add(new Turn(Instant.now(), "First", "Reply one"));
		given(transcriptRepository.findById(CALL_SID)).willReturn(Optional.of(existing));
		given(transcriptRepository.save(existing)).willReturn(existing);

		transcriptService.appendTurn(CALL_SID, "+15551234567", "Second", "Reply two");

		assertThat(existing.getTurns()).hasSize(2);
		verify(transcriptRepository).save(existing);
	}

}
