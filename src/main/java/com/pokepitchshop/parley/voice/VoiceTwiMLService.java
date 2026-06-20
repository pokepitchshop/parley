package com.pokepitchshop.parley.voice;

import org.springframework.stereotype.Service;

import com.twilio.http.HttpMethod;
import com.twilio.twiml.TwiMLException;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Gather;
import com.twilio.twiml.voice.Say;

@Service
public class VoiceTwiMLService {

	static final String OPENING_GREETING = """
			Hi, you're through to Poke Pitch Shop. How can I help you today?
			""";

	static final String RESPOND_ACTION = "/voice/respond";

	public String openingResponse() throws TwiMLException {
		Say greeting = new Say.Builder(OPENING_GREETING.trim())
				.voice(Say.Voice.POLLY_JOANNA_NEURAL)
				.build();
		Gather gather = new Gather.Builder()
				.action(RESPOND_ACTION)
				.method(HttpMethod.POST)
				.inputs(Gather.Input.SPEECH)
				.build();
		VoiceResponse response = new VoiceResponse.Builder()
				.say(greeting)
				.gather(gather)
				.build();
		return response.toXml();
	}

}
