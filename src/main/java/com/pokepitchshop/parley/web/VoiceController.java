package com.pokepitchshop.parley.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pokepitchshop.parley.relay.ConversationRelayPaths;
import com.pokepitchshop.parley.relay.RelayTwiMLService;
import com.pokepitchshop.parley.transcript.CallSummaryService;
import com.pokepitchshop.parley.voice.VoiceProperties;
import com.pokepitchshop.parley.voice.VoiceTwiMLService;
import com.twilio.twiml.TwiMLException;

@RestController
public class VoiceController {

	private final VoiceTwiMLService voiceTwiMLService;

	private final RelayTwiMLService relayTwiMLService;

	private final CallSummaryService callSummaryService;

	private final VoiceProperties voiceProperties;

	public VoiceController(
			VoiceTwiMLService voiceTwiMLService,
			RelayTwiMLService relayTwiMLService,
			CallSummaryService callSummaryService,
			VoiceProperties voiceProperties) {
		this.voiceTwiMLService = voiceTwiMLService;
		this.relayTwiMLService = relayTwiMLService;
		this.callSummaryService = callSummaryService;
		this.voiceProperties = voiceProperties;
	}

	@PostMapping(value = "/voice", produces = MediaType.APPLICATION_XML_VALUE)
	String voice(@RequestParam(value = "From", required = false) String fromNumber) throws TwiMLException {
		if (voiceProperties.isTurnBased()) {
			return voiceTwiMLService.openingResponse(fromNumber);
		}
		return relayTwiMLService.conversationRelayConnect(fromNumber);
	}

	@PostMapping(value = ConversationRelayPaths.RELAY_TWIML_PATH, produces = MediaType.APPLICATION_XML_VALUE)
	String relay(@RequestParam(value = "From", required = false) String fromNumber) throws TwiMLException {
		return relayTwiMLService.conversationRelayConnect(fromNumber);
	}

	@PostMapping(value = "/voice/respond", produces = MediaType.APPLICATION_XML_VALUE)
	String respond(
			@RequestParam(value = "CallSid", required = false) String callSid,
			@RequestParam(value = "From", required = false) String fromNumber,
			@RequestParam(value = "SpeechResult", required = false) String speechResult)
			throws TwiMLException {
		return voiceTwiMLService.respond(callSid, fromNumber, speechResult);
	}

	@PostMapping(value = "/voice/reply", produces = MediaType.APPLICATION_XML_VALUE)
	String reply(@RequestParam(value = "CallSid", required = false) String callSid) throws TwiMLException {
		return voiceTwiMLService.reply(callSid);
	}

	@PostMapping("/voice/status")
	ResponseEntity<Void> status(
			@RequestParam(value = "CallSid", required = false) String callSid,
			@RequestParam(value = "CallStatus", required = false) String callStatus) {
		if (callSid != null && "completed".equalsIgnoreCase(callStatus)) {
			callSummaryService.onCallCompleted(callSid);
		}
		return ResponseEntity.ok().build();
	}

}
