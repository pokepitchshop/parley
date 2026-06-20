package com.pokepitchshop.parley.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pokepitchshop.parley.voice.VoiceTwiMLService;

@WebMvcTest(VoiceController.class)
class VoiceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VoiceTwiMLService voiceTwiMLService;

	@Test
	void voiceReturnsOpeningTwiml() throws Exception {
		String twiml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<Response>
				<Say voice="Polly.Joanna-Neural">Hi, you're through to Poke Pitch Shop. How can I help you today?</Say>
				<Gather input="speech" action="/voice/respond" method="POST"/>
				</Response>
				""";

		given(voiceTwiMLService.openingResponse()).willReturn(twiml);

		mockMvc.perform(post("/voice"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
				.andExpect(content().xml(twiml));
	}

	@Test
	void respondReturnsConversationTwiml() throws Exception {
		String twiml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<Response>
				<Say voice="Polly.Joanna-Neural">We are open until six.</Say>
				<Gather input="speech" action="/voice/respond" method="POST"/>
				</Response>
				""";

		given(voiceTwiMLService.respond(eq("CA123"), eq("What are your hours?"))).willReturn(twiml);

		mockMvc.perform(post("/voice/respond")
						.param("CallSid", "CA123")
						.param("SpeechResult", "What are your hours?"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
				.andExpect(content().xml(twiml));
	}

	@Test
	void respondWithNoSpeechRedirectsToVoice() throws Exception {
		String twiml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<Response><Redirect method="POST">/voice</Redirect></Response>
				""";

		given(voiceTwiMLService.respond(null, null)).willReturn(twiml);

		mockMvc.perform(post("/voice/respond"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
				.andExpect(content().xml(twiml));
	}

}
