package com.pokepitchshop.parley.caller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import com.pokepitchshop.parley.transcript.Transcript;
import com.pokepitchshop.parley.transcript.Turn;

@ExtendWith(MockitoExtension.class)
class CallerServiceTest {

	private static final String PHONE = "+15551234567";

	@Mock
	private CallerRepository callerRepository;

	@Mock
	private ChatClient summaryChatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec responseSpec;

	private CallerService callerService;

	@BeforeEach
	void setUp() {
		callerService = new CallerService(callerRepository, summaryChatClient);
	}

	@Test
	void contextForUnknownNumberReturnsAnonymous() {
		given(callerRepository.findById(PHONE)).willReturn(Optional.empty());

		CallerContext context = callerService.contextFor(PHONE);

		assertThat(context.isReturning()).isFalse();
		assertThat(context.openingGreeting()).isEqualTo(CallerContext.DEFAULT_OPENING.trim());
	}

	@Test
	void contextForKnownCallerWithNameReturnsPersonalizedGreeting() {
		Caller caller = new Caller(PHONE);
		caller.setDisplayName("Alex");
		caller.setLastSummary("Asked about store hours.");
		given(callerRepository.findById(PHONE)).willReturn(Optional.of(caller));

		CallerContext context = callerService.contextFor(PHONE);

		assertThat(context.openingGreeting())
				.isEqualTo("Hi Alex, welcome back to Poke Pitch Shop. What can I help you with?");
		assertThat(context.systemPromptSnippet()).contains("Their name is Alex.");
		assertThat(context.systemPromptSnippet()).contains("Asked about store hours.");
	}

	@Test
	void contextForKnownCallerWithoutNameUsesWelcomeBackGreeting() {
		Caller caller = new Caller(PHONE);
		caller.setLastSummary("Asked about store hours.");
		given(callerRepository.findById(PHONE)).willReturn(Optional.of(caller));

		CallerContext context = callerService.contextFor(PHONE);

		assertThat(context.openingGreeting())
				.isEqualTo("Welcome back to Poke Pitch Shop. Good to hear from you again. What can I help you with?");
	}

	@Test
	void updateAfterCallUpsertsSummaryAndExtractedName() {
		Transcript transcript = new Transcript("CA123", PHONE, Instant.now());
		transcript.getTurns().add(new Turn(Instant.now(), "I'm Sam.", "Hi Sam, how can I help?"));

		given(callerRepository.findById(PHONE)).willReturn(Optional.empty());
		given(summaryChatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.system(anyString())).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(responseSpec);
		given(responseSpec.content()).willReturn("Sam");

		callerService.updateAfterCall(transcript, "Caller Sam asked about hours.");

		ArgumentCaptor<Caller> callerCaptor = ArgumentCaptor.forClass(Caller.class);
		verify(callerRepository).save(callerCaptor.capture());
		Caller saved = callerCaptor.getValue();
		assertThat(saved.getPhoneNumber()).isEqualTo(PHONE);
		assertThat(saved.getDisplayName()).isEqualTo("Sam");
		assertThat(saved.getLastSummary()).isEqualTo("Caller Sam asked about hours.");
		assertThat(saved.getLastCallAt()).isNotNull();
	}

	@Test
	void updateAfterCallSkipsWhenFromNumberMissing() {
		Transcript transcript = new Transcript("CA123", null, Instant.now());

		callerService.updateAfterCall(transcript, "Summary.");

		verify(callerRepository, never()).save(any());
	}

	@Test
	void updateAfterCallPreservesExistingNameWhenExtractionReturnsNone() {
		Transcript transcript = new Transcript("CA123", PHONE, Instant.now());
		transcript.getTurns().add(new Turn(Instant.now(), "What are your hours?", "Until six."));

		Caller existing = new Caller(PHONE);
		existing.setDisplayName("Alex");
		given(callerRepository.findById(PHONE)).willReturn(Optional.of(existing));
		given(summaryChatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.system(anyString())).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(responseSpec);
		given(responseSpec.content()).willReturn("NONE");

		callerService.updateAfterCall(transcript, "Asked about hours.");

		ArgumentCaptor<Caller> callerCaptor = ArgumentCaptor.forClass(Caller.class);
		verify(callerRepository).save(callerCaptor.capture());
		assertThat(callerCaptor.getValue().getDisplayName()).isEqualTo("Alex");
	}

}
