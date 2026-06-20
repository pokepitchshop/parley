package com.pokepitchshop.parley.web;

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

		org.mockito.BDDMockito.given(voiceTwiMLService.openingResponse()).willReturn(twiml);

		mockMvc.perform(post("/voice"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
				.andExpect(content().xml(twiml));
	}

}
