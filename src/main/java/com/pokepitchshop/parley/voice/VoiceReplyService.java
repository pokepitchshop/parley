package com.pokepitchshop.parley.voice;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.pokepitchshop.parley.caller.CallerContext;
import com.pokepitchshop.parley.caller.CallerService;
import com.pokepitchshop.parley.guardrails.AgentGuardrails;
import com.pokepitchshop.parley.guardrails.CallLimitService;
import com.pokepitchshop.parley.guardrails.OutOfScopeDetector;
import com.pokepitchshop.parley.guardrails.ToolCallGuardrail;
import com.pokepitchshop.parley.guardrails.ToolTurnDetector;
import com.pokepitchshop.parley.transcript.TranscriptService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class VoiceReplyService {

	private final ChatClient chatClient;

	private final TranscriptService transcriptService;

	private final CallerService callerService;

	private final CallLimitService callLimitService;

	private final OutOfScopeDetector outOfScopeDetector;

	private final ToolTurnDetector toolTurnDetector;

	private final ToolCallGuardrail toolCallGuardrail;

	public VoiceReplyService(
			ChatClient chatClient,
			TranscriptService transcriptService,
			CallerService callerService,
			CallLimitService callLimitService,
			OutOfScopeDetector outOfScopeDetector,
			ToolTurnDetector toolTurnDetector,
			ToolCallGuardrail toolCallGuardrail) {
		this.chatClient = chatClient;
		this.transcriptService = transcriptService;
		this.callerService = callerService;
		this.callLimitService = callLimitService;
		this.outOfScopeDetector = outOfScopeDetector;
		this.toolTurnDetector = toolTurnDetector;
		this.toolCallGuardrail = toolCallGuardrail;
	}

	public String replyToUtterance(String callSid, String fromNumber, String speechResult) {
		if (callLimitService.hasReachedTurnLimit(callSid)) {
			return AgentGuardrails.CALL_LIMIT_CLOSING.trim();
		}
		var cannedDecline = outOfScopeDetector.cannedDecline(speechResult);
		if (cannedDecline.isPresent()) {
			String reply = cannedDecline.get();
			transcriptService.appendTurn(callSid, fromNumber, speechResult, reply);
			return reply;
		}
		if (toolTurnDetector.looksLikeToolAction(speechResult) && toolCallGuardrail.isBlocked(callSid)) {
			String reply = toolCallGuardrail.blockedToolMessage();
			transcriptService.appendTurn(callSid, fromNumber, speechResult, reply);
			return reply;
		}
		return generateReply(callSid, fromNumber, speechResult);
	}

	private String generateReply(String callSid, String fromNumber, String speechResult) {
		CallerContext callerContext = callerService.contextFor(fromNumber);
		try {
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
			return reply;
		}
		catch (DataAccessException ex) {
			log.error("Transcript save failed for CallSid={}", callSid, ex);
			return AgentGuardrails.LLM_UNAVAILABLE.trim();
		}
		catch (RuntimeException ex) {
			log.error("LLM turn failed for CallSid={}", callSid, ex);
			return AgentGuardrails.LLM_UNAVAILABLE.trim();
		}
	}
}
