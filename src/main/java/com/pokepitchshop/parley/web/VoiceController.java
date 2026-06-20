package com.pokepitchshop.parley.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pokepitchshop.parley.voice.VoiceTwiMLService;
import com.twilio.twiml.TwiMLException;

@RestController
public class VoiceController {

	private final VoiceTwiMLService voiceTwiMLService;

	public VoiceController(VoiceTwiMLService voiceTwiMLService) {
		this.voiceTwiMLService = voiceTwiMLService;
	}

	@PostMapping(value = "/voice", produces = MediaType.APPLICATION_XML_VALUE)
	String voice() throws TwiMLException {
		return voiceTwiMLService.openingResponse();
	}

	@PostMapping(value = "/voice/respond", produces = MediaType.APPLICATION_XML_VALUE)
	String respond(@RequestParam(value = "SpeechResult", required = false) String speechResult)
			throws TwiMLException {
		return voiceTwiMLService.respond(speechResult);
	}

}
