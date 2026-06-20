package com.pokepitchshop.parley.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import com.pokepitchshop.parley.caller.CallerContext;
import com.pokepitchshop.parley.caller.CallerService;
import com.pokepitchshop.parley.transcript.TranscriptService;

@ExtendWith(MockitoExtension.class)
class VoiceTwiMLServiceTest {

	private static final String CALL_SID = "CA1234567890abcdef";

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec responseSpec;

	@Mock
	private TranscriptService transcriptService;

	@Mock
	private CallerService callerService;

	private VoiceTwiMLService service;

	@BeforeEach
	void setUp() {
		VoiceProperties voiceProperties = new VoiceProperties();
		voiceProperties.setSayVoice("POLLY_JOANNA_NEURAL");
		voiceProperties.setSpeechTimeout(3);
		service = new VoiceTwiMLService(chatClient, voiceProperties, transcriptService, callerService);
	}

	@Test
	void openingResponseContainsSayGatherSpeechTimeoutAndRespondAction() throws Exception {
		given(callerService.contextFor(null)).willReturn(CallerContext.anonymous());

		String twiml = service.openingResponse(null);

		assertThat(twiml).contains("<Response>");
		assertThat(twiml).contains("<Say");
		assertThat(twiml).contains("Polly.Joanna-Neural");
		assertThat(twiml).contains(CallerContext.DEFAULT_OPENING.trim());
		assertThat(twiml).contains("<Gather");
		assertThat(twiml).contains("input=\"speech\"");
		assertThat(twiml).contains("speechTimeout=\"3\"");
		assertThat(twiml).contains("action=\"/voice/respond\"");
		assertThat(twiml).contains("method=\"POST\"");
	}

	@Test
	void openingResponseGreetsKnownCallerByName() throws Exception {
		CallerContext context = new CallerContext("Alex", "Asked about hours.");
		given(callerService.contextFor("+15551234567")).willReturn(context);

		String twiml = service.openingResponse("+15551234567");

		assertThat(twiml).contains("Hi Alex, welcome back to Poke Pitch Shop.");
	}

	@Test
	void respondWithSpeechReturnsReplySayAndGather() throws Exception {
		CallerContext context = CallerContext.anonymous();
		given(callerService.contextFor("+15551234567")).willReturn(context);
		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.system(anyString())).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.advisors(any(Consumer.class))).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(responseSpec);
		given(responseSpec.content()).willReturn("We are open until six.");

		String twiml = service.respond(CALL_SID, "+15551234567", "What are your hours?");

		assertThat(twiml).contains("We are open until six.");
		assertThat(twiml).contains("<Gather");
		assertThat(twiml).contains("speechTimeout=\"3\"");
		assertThat(twiml).contains("action=\"/voice/respond\"");
		verify(requestSpec).system(context.systemPromptSnippet());
		verify(requestSpec).advisors(any(Consumer.class));
		verify(transcriptService).appendTurn(CALL_SID, "+15551234567", "What are your hours?", "We are open until six.");
	}

	@Test
	void respondWithEmptySpeechRedirectsToVoice() throws Exception {
		String twiml = service.respond(CALL_SID, "+15551234567", "");

		assertThat(twiml).contains("<Redirect");
		assertThat(twiml).contains("/voice");
		assertThat(twiml).contains("method=\"POST\"");
	}

	@Test
	void respondWithNullSpeechRedirectsToVoice() throws Exception {
		String twiml = service.respond(CALL_SID, "+15551234567", null);

		assertThat(twiml).contains("<Redirect");
		assertThat(twiml).contains("/voice");
	}

}
