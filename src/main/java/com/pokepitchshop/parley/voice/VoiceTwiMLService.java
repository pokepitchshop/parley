package com.pokepitchshop.parley.voice;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import com.pokepitchshop.parley.caller.CallerContext;
import com.pokepitchshop.parley.caller.CallerService;
import com.pokepitchshop.parley.guardrails.AgentGuardrails;
import com.pokepitchshop.parley.guardrails.CallLimitService;
import com.pokepitchshop.parley.guardrails.OutOfScopeDetector;
import com.pokepitchshop.parley.guardrails.ToolCallGuardrail;
import com.pokepitchshop.parley.guardrails.ToolTurnDetector;
import com.pokepitchshop.parley.transcript.TranscriptService;
import com.twilio.http.HttpMethod;
import com.twilio.twiml.TwiMLException;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Gather;
import com.twilio.twiml.voice.Hangup;
import com.twilio.twiml.voice.Redirect;
import com.twilio.twiml.voice.Say;

@Service
public class VoiceTwiMLService {

	static final String VOICE_PATH = "/voice";

	static final String RESPOND_ACTION = "/voice/respond";

	private final ChatClient chatClient;

	private final VoiceProperties voiceProperties;

	private final TranscriptService transcriptService;

	private final CallerService callerService;

	private final CallLimitService callLimitService;

	private final OutOfScopeDetector outOfScopeDetector;

	private final ToolTurnDetector toolTurnDetector;

	private final ToolCallGuardrail toolCallGuardrail;

	public VoiceTwiMLService(
			ChatClient chatClient,
			VoiceProperties voiceProperties,
			TranscriptService transcriptService,
			CallerService callerService,
			CallLimitService callLimitService,
			OutOfScopeDetector outOfScopeDetector,
			ToolTurnDetector toolTurnDetector,
			ToolCallGuardrail toolCallGuardrail) {
		this.chatClient = chatClient;
		this.voiceProperties = voiceProperties;
		this.transcriptService = transcriptService;
		this.callerService = callerService;
		this.callLimitService = callLimitService;
		this.outOfScopeDetector = outOfScopeDetector;
		this.toolTurnDetector = toolTurnDetector;
		this.toolCallGuardrail = toolCallGuardrail;
	}

	public String openingResponse(String fromNumber) throws TwiMLException {
		String greetingText = callerService.contextFor(fromNumber).openingGreeting();
		Say greeting = new Say.Builder(greetingText)
				.voice(sayVoice())
				.build();
		VoiceResponse response = new VoiceResponse.Builder()
				.say(greeting)
				.gather(speechGather())
				.build();
		return response.toXml();
	}

	public String respond(String callSid, String fromNumber, String speechResult) throws TwiMLException {
		if (speechResult == null || speechResult.isBlank()) {
			return redirectToOpening();
		}
		if (callLimitService.hasReachedTurnLimit(callSid)) {
			return callLimitClosingResponse();
		}
		var cannedDecline = outOfScopeDetector.cannedDecline(speechResult);
		if (cannedDecline.isPresent()) {
			String reply = cannedDecline.get();
			transcriptService.appendTurn(callSid, fromNumber, speechResult, reply);
			return conversationTurnResponse(reply);
		}
		if (toolTurnDetector.looksLikeToolAction(speechResult) && toolCallGuardrail.isBlocked(callSid)) {
			String reply = toolCallGuardrail.blockedToolMessage();
			transcriptService.appendTurn(callSid, fromNumber, speechResult, reply);
			return conversationTurnResponse(reply);
		}
		CallerContext callerContext = callerService.contextFor(fromNumber);
		var prompt = chatClient.prompt()
				.system(callerContext.systemPromptSnippet());
		if (toolTurnDetector.looksLikeToolAction(speechResult)) {
			prompt = prompt.system(AgentGuardrails.TOOL_TURN_HINT);
		}
		String reply = prompt
				.user(speechResult)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, callSid))
				.call()
				.content();
		transcriptService.appendTurn(callSid, fromNumber, speechResult, reply);
		return conversationTurnResponse(reply);
	}

	public String callLimitClosingResponse() throws TwiMLException {
		Say say = new Say.Builder(AgentGuardrails.CALL_LIMIT_CLOSING.trim())
				.voice(sayVoice())
				.build();
		Hangup hangup = new Hangup.Builder().build();
		VoiceResponse response = new VoiceResponse.Builder()
				.say(say)
				.hangup(hangup)
				.build();
		return response.toXml();
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
