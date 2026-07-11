package com.pokepitchshop.parley.voice;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.pokepitchshop.parley.guardrails.AgentGuardrails;
import com.pokepitchshop.parley.shop.TurnContext;
import com.pokepitchshop.parley.shop.TurnContextService;
import com.pokepitchshop.parley.guardrails.CallLimitService;
import com.pokepitchshop.parley.guardrails.OutOfScopeDetector;
import com.pokepitchshop.parley.guardrails.ToolCallGuardrail;
import com.pokepitchshop.parley.guardrails.ToolTurnDetector;
import com.pokepitchshop.parley.transcript.TranscriptService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class VoiceReplyService {

	private final ChatClient chatClient;

	private final TranscriptService transcriptService;

	private final TurnContextService turnContextService;

	private final CallLimitService callLimitService;

	private final OutOfScopeDetector outOfScopeDetector;

	private final ToolTurnDetector toolTurnDetector;

	private final ToolCallGuardrail toolCallGuardrail;

	public VoiceReplyService(
			ChatClient chatClient,
			TranscriptService transcriptService,
			TurnContextService turnContextService,
			CallLimitService callLimitService,
			OutOfScopeDetector outOfScopeDetector,
			ToolTurnDetector toolTurnDetector,
			ToolCallGuardrail toolCallGuardrail) {
		this.chatClient = chatClient;
		this.transcriptService = transcriptService;
		this.turnContextService = turnContextService;
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

	public void streamReplyToUtterance(
			String callSid,
			String fromNumber,
			String speechResult,
			VoiceReplyChunkConsumer consumer) {
		streamReplyToUtterance(callSid, fromNumber, speechResult, consumer, null);
	}

	public void streamReplyToUtterance(
			String callSid,
			String fromNumber,
			String speechResult,
			VoiceReplyChunkConsumer consumer,
			TurnLatencyTracker latency) {
		if (callLimitService.hasReachedTurnLimit(callSid)) {
			consumer.onChunk(AgentGuardrails.CALL_LIMIT_CLOSING.trim(), true);
			return;
		}
		var cannedDecline = outOfScopeDetector.cannedDecline(speechResult);
		if (cannedDecline.isPresent()) {
			String reply = cannedDecline.get();
			transcriptService.appendTurn(callSid, fromNumber, speechResult, reply);
			consumer.onChunk(reply, true);
			return;
		}
		if (toolTurnDetector.looksLikeToolAction(speechResult) && toolCallGuardrail.isBlocked(callSid)) {
			String reply = toolCallGuardrail.blockedToolMessage();
			transcriptService.appendTurn(callSid, fromNumber, speechResult, reply);
			consumer.onChunk(reply, true);
			return;
		}
		streamGenerateReply(callSid, fromNumber, speechResult, consumer, latency);
	}

	private String generateReply(String callSid, String fromNumber, String speechResult) {
		try {
			String reply = requestSpec(callSid, turnContextService.forCaller(fromNumber), speechResult)
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

	private void streamGenerateReply(
			String callSid,
			String fromNumber,
			String speechResult,
			VoiceReplyChunkConsumer consumer,
			TurnLatencyTracker latency) {
		SpokenSentenceChunker chunker = new SpokenSentenceChunker();
		StringBuilder fullReply = new StringBuilder();
		AtomicReference<String> pendingSentence = new AtomicReference<>();
		try {
			Flux<String> tokens = requestSpec(callSid, turnContextService.forCaller(fromNumber), speechResult)
					.stream()
					.content();
			tokens.doOnNext(token -> {
				if (latency != null) {
					latency.markLlmFirstToken();
				}
				fullReply.append(token);
				for (String sentence : chunker.appendAndDrainSentences(token)) {
					pendingSentence.set(emitPendingSentence(pendingSentence.get(), sentence, consumer));
				}
			}).blockLast();
			if (latency != null) {
				latency.markLlmComplete();
			}
			finishStreaming(chunker, pendingSentence.get(), consumer);
			transcriptService.appendTurn(callSid, fromNumber, speechResult, fullReply.toString());
		}
		catch (VoiceReplyStreamCancelledException ex) {
			log.debug("ConversationRelay reply cancelled callSid={}", callSid);
		}
		catch (DataAccessException ex) {
			log.error("Transcript save failed for CallSid={}", callSid, ex);
			consumer.onChunk(AgentGuardrails.LLM_UNAVAILABLE.trim(), true);
		}
		catch (RuntimeException ex) {
			log.error("LLM stream failed for CallSid={}", callSid, ex);
			consumer.onChunk(AgentGuardrails.LLM_UNAVAILABLE.trim(), true);
		}
	}

	private ChatClient.ChatClientRequestSpec requestSpec(
			String callSid,
			TurnContext turnContext,
			String speechResult) {
		var prompt = chatClient.prompt()
				.system(turnContext.shopSnippet())
				.system(turnContext.callerSnippet());
		if (toolTurnDetector.looksLikeToolAction(speechResult)) {
			prompt = prompt.system(AgentGuardrails.TOOL_TURN_HINT);
		}
		return prompt
				.user(speechResult)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, callSid));
	}

	private String emitPendingSentence(
			String pendingSentence,
			String completedSentence,
			VoiceReplyChunkConsumer consumer) {
		if (pendingSentence != null && !consumer.onChunk(pendingSentence, false)) {
			throw new VoiceReplyStreamCancelledException();
		}
		return completedSentence;
	}

	private void finishStreaming(
			SpokenSentenceChunker chunker,
			String pendingSentence,
			VoiceReplyChunkConsumer consumer) {
		var remainder = chunker.flushRemainder();
		if (remainder.isPresent()) {
			if (pendingSentence != null && !consumer.onChunk(pendingSentence, false)) {
				throw new VoiceReplyStreamCancelledException();
			}
			if (!consumer.onChunk(remainder.get(), true)) {
				throw new VoiceReplyStreamCancelledException();
			}
			return;
		}
		if (pendingSentence != null && !consumer.onChunk(pendingSentence, true)) {
			throw new VoiceReplyStreamCancelledException();
		}
	}
}
