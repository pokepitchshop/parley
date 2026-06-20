package com.pokepitchshop.parley.voice;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
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
			Hi, you're through to Poke Pitch Shop. What can I help you with?
			""";

	static final String VOICE_PATH = "/voice";

	static final String RESPOND_ACTION = "/voice/respond";

	private final ChatClient chatClient;

	private final VoiceProperties voiceProperties;

	public VoiceTwiMLService(ChatClient chatClient, VoiceProperties voiceProperties) {
		this.chatClient = chatClient;
		this.voiceProperties = voiceProperties;
	}

	public String openingResponse() throws TwiMLException {
		Say greeting = new Say.Builder(OPENING_GREETING.trim())
				.voice(sayVoice())
				.build();
		VoiceResponse response = new VoiceResponse.Builder()
				.say(greeting)
				.gather(speechGather())
				.build();
		return response.toXml();
	}

	public String respond(String callSid, String speechResult) throws TwiMLException {
		if (speechResult == null || speechResult.isBlank()) {
			return redirectToOpening();
		}
		String reply = chatClient.prompt()
				.user(speechResult)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, callSid))
				.call()
				.content();
		return conversationTurnResponse(reply);
	}

	public String conversationTurnResponse(String reply) throws TwiMLException {
		Say say = new Say.Builder(reply)
				.voice(sayVoice())
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
				.speechTimeout(String.valueOf(voiceProperties.getSpeechTimeout()))
				.build();
	}

	private Say.Voice sayVoice() {
		try {
			return Say.Voice.valueOf(voiceProperties.getSayVoice());
		}
		catch (IllegalArgumentException ex) {
			return Say.Voice.POLLY_JOANNA_NEURAL;
		}
	}

}
