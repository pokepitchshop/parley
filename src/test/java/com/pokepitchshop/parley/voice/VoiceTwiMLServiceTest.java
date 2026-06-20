package com.pokepitchshop.parley.voice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VoiceTwiMLServiceTest {

	private final VoiceTwiMLService service = new VoiceTwiMLService();

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

}
