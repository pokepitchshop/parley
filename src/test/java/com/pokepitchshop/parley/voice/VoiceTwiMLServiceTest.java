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

@ExtendWith(MockitoExtension.class)
class VoiceTwiMLServiceTest {

	private static final String CALL_SID = "CA1234567890abcdef";

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec responseSpec;

	private VoiceTwiMLService service;

	@BeforeEach
	void setUp() {
		service = new VoiceTwiMLService(chatClient);
	}

	@Test
	void openingResponseContainsSayGatherAndRespondAction() throws Exception {
		String twiml = service.openingResponse();

		assertThat(twiml).contains("<Response>");
		assertThat(twiml).contains("<Say");
		assertThat(twiml).contains("Polly.Joanna-Neural");
		assertThat(twiml).contains(VoiceTwiMLService.OPENING_GREETING.trim());
		assertThat(twiml).contains("<Gather");
		assertThat(twiml).contains("input=\"speech\"");
		assertThat(twiml).contains("action=\"/voice/respond\"");
		assertThat(twiml).contains("method=\"POST\"");
	}

	@Test
	void respondWithSpeechReturnsReplySayAndGather() throws Exception {
		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.advisors(any(Consumer.class))).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(responseSpec);
		given(responseSpec.content()).willReturn("We are open until six.");

		String twiml = service.respond(CALL_SID, "What are your hours?");

		assertThat(twiml).contains("We are open until six.");
		assertThat(twiml).contains("<Gather");
		assertThat(twiml).contains("action=\"/voice/respond\"");
		verify(requestSpec).advisors(any(Consumer.class));
	}

	@Test
	void respondWithEmptySpeechRedirectsToVoice() throws Exception {
		String twiml = service.respond(CALL_SID, "");

		assertThat(twiml).contains("<Redirect");
		assertThat(twiml).contains("/voice");
		assertThat(twiml).contains("method=\"POST\"");
	}

	@Test
	void respondWithNullSpeechRedirectsToVoice() throws Exception {
		String twiml = service.respond(CALL_SID, null);

		assertThat(twiml).contains("<Redirect");
		assertThat(twiml).contains("/voice");
	}

}
