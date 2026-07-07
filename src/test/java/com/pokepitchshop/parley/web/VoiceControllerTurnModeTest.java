package com.pokepitchshop.parley.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pokepitchshop.parley.relay.RelayTwiMLService;
import com.pokepitchshop.parley.transcript.CallSummaryService;
import com.pokepitchshop.parley.twilio.TwilioSignatureFilter;
import com.pokepitchshop.parley.voice.VoiceConfig;
import com.pokepitchshop.parley.voice.VoiceTwiMLService;

@WebMvcTest(
		controllers = VoiceController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = TwilioSignatureFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(VoiceConfig.class)
@TestPropertySource(properties = "parley.voice.mode=turn")
class VoiceControllerTurnModeTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VoiceTwiMLService voiceTwiMLService;

	@MockitoBean
	private RelayTwiMLService relayTwiMLService;

	@MockitoBean
	private CallSummaryService callSummaryService;

	@Test
	void voiceReturnsOpeningTwimlInTurnMode() throws Exception {
		String twiml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<Response>
				<Say voice="Polly.Joanna-Neural">Hi, you're through to Poke Pitch Shop. How can I help you today?</Say>
				<Gather input="speech" action="/voice/respond" method="POST"/>
				</Response>
				""";

		given(voiceTwiMLService.openingResponse(null)).willReturn(twiml);

		mockMvc.perform(post("/voice"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
				.andExpect(content().xml(twiml));
	}
}
