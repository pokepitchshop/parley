package com.pokepitchshop.parley.relay;

import org.springframework.stereotype.Service;

import com.pokepitchshop.parley.caller.CallerService;
import com.pokepitchshop.parley.twilio.TwilioSignatureValidator;
import com.twilio.twiml.TwiMLException;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Connect;
import com.twilio.twiml.voice.ConversationRelay;

@Service
public class RelayTwiMLService {

	private final CallerService callerService;

	private final TwilioSignatureValidator signatureValidator;

	public RelayTwiMLService(CallerService callerService, TwilioSignatureValidator signatureValidator) {
		this.callerService = callerService;
		this.signatureValidator = signatureValidator;
	}

	public String conversationRelayConnect(String fromNumber) throws TwiMLException {
		String welcomeGreeting = callerService.contextFor(fromNumber).openingGreeting();
		Connect connect = new Connect.Builder()
				.conversationRelay(new ConversationRelay.Builder()
						.url(signatureValidator.relayWebSocketUrl())
						.welcomeGreeting(welcomeGreeting)
						.build())
				.build();
		return new VoiceResponse.Builder()
				.connect(connect)
				.build()
				.toXml();
	}
}
