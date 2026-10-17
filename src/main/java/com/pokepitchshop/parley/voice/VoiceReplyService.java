package com.pokepitchshop.parley.voice;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
			String reply = requestSpec(callSid, turnContextService.forCaller(fromNumber, speechResult), speechResult)
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
			Flux<String> tokens = requestSpec(callSid, turnContextService.forCaller(fromNumber, speechResult), speechResult)
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
		return chatClient.prompt()
				.system(systemText(turnContext, speechResult))
				.user(speechResult)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, callSid));
	}

	/**
	 * Each {@code system(...)} call replaces the previous system text, including the client's
	 * default, so every part of the system prompt has to go in a single call.
	 */
	private String systemText(TurnContext turnContext, String speechResult) {
		var parts = Stream.of(
				AgentGuardrails.VOICE_SYSTEM_PROMPT,
				AgentGuardrails.STOCK_RULES,
				turnContext.shopSnippet(),
				turnContext.callerSnippet(),
				turnContext.inventorySnippet(),
				toolTurnDetector.looksLikeToolAction(speechResult) ? AgentGuardrails.TOOL_TURN_HINT : null);
		return parts
				.filter(StringUtils::hasText)
				.map(String::trim)
				.collect(Collectors.joining("\n\n"));
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
