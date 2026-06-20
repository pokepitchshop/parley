package com.pokepitchshop.parley.voice;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.twilio.http.HttpMethod;
import com.twilio.twiml.TwiMLException;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Gather;
import com.twilio.twiml.voice.Redirect;
import com.twilio.twiml.voice.Say;

@Service
public class VoiceTwiMLService {

	static final String OPENING_GREETING = """
			Hi, you're through to Poke Pitch Shop. How can I help you today?
			""";

	static final String VOICE_PATH = "/voice";

	static final String RESPOND_ACTION = "/voice/respond";

	private final ChatClient chatClient;

	public VoiceTwiMLService(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	public String openingResponse() throws TwiMLException {
		Say greeting = new Say.Builder(OPENING_GREETING.trim())
				.voice(Say.Voice.POLLY_JOANNA_NEURAL)
				.build();
		VoiceResponse response = new VoiceResponse.Builder()
				.say(greeting)
				.gather(speechGather())
				.build();
		return response.toXml();
	}

	public String respond(String speechResult) throws TwiMLException {
		if (speechResult == null || speechResult.isBlank()) {
			return redirectToOpening();
		}
		String reply = chatClient.prompt().user(speechResult).call().content();
		return conversationTurnResponse(reply);
	}

	public String conversationTurnResponse(String reply) throws TwiMLException {
		Say say = new Say.Builder(reply)
				.voice(Say.Voice.POLLY_JOANNA_NEURAL)
				.build();
		VoiceResponse response = new VoiceResponse.Builder()
				.say(say)
				.gather(speechGather())
				.build();
		return response.toXml();
	}

	public String redirectToOpening() throws TwiMLException {
		Redirect redirect = new Redirect.Builder(VOICE_PATH)
				.method(HttpMethod.POST)
				.build();
		VoiceResponse response = new VoiceResponse.Builder()
				.redirect(redirect)
				.build();
		return response.toXml();
	}

	private Gather speechGather() {
		return new Gather.Builder()
				.action(RESPOND_ACTION)
				.method(HttpMethod.POST)
				.inputs(Gather.Input.SPEECH)
				.build();
	}

}
