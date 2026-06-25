package com.pokepitchshop.parley.relay;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokepitchshop.parley.voice.VoiceReplyService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ConversationRelayHandler extends TextWebSocketHandler {

	private final VoiceReplyService voiceReplyService;

	private final ObjectMapper objectMapper;

	private final Executor replyExecutor;

	@Autowired
	public ConversationRelayHandler(VoiceReplyService voiceReplyService) {
		this.voiceReplyService = voiceReplyService;
		this.replyExecutor = Executors.newVirtualThreadPerTaskExecutor();
		this.objectMapper = new ObjectMapper();
	}

	ConversationRelayHandler(VoiceReplyService voiceReplyService, Executor replyExecutor) {
		this.voiceReplyService = voiceReplyService;
		this.replyExecutor = replyExecutor;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		log.info("ConversationRelay WebSocket connected sessionId={}", session.getId());
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		RelayInboundMessage inbound = objectMapper.readValue(message.getPayload(), RelayInboundMessage.class);
		switch (inbound.type()) {
			case "setup" -> handleSetup(session, inbound);
			case "prompt" -> handlePrompt(session, inbound);
			case "interrupt" -> handleInterrupt(session, inbound);
			case "error" -> log.warn("ConversationRelay error sessionId={} description={}",
					session.getId(), inbound.description());
			case "dtmf" -> log.debug("ConversationRelay DTMF sessionId={}", session.getId());
			default -> log.debug("ConversationRelay ignored message type={} sessionId={}",
					inbound.type(), session.getId());
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		RelaySessionState.invalidateInFlight(session);
		RelayTurnLatency latency = RelaySessionState.currentTurnLatency(session);
		if (latency != null) {
			latency.markInterrupted();
			latency.logTurn();
			RelaySessionState.clearCurrentTurnLatency(session);
		}
		String callSid = (String) session.getAttributes().get(RelaySessionAttributes.CALL_SID);
		log.info("ConversationRelay WebSocket closed callSid={} sessionId={} status={}",
				callSid, session.getId(), status);
	}

	private void handleSetup(WebSocketSession session, RelayInboundMessage inbound) {
		session.getAttributes().put(RelaySessionAttributes.CALL_SID, inbound.callSid());
		session.getAttributes().put(RelaySessionAttributes.FROM, inbound.from());
		session.getAttributes().put(RelaySessionAttributes.SESSION_ID, inbound.sessionId());
		RelaySessionState.generationCounter(session);
		RelaySessionState.turnCounter(session);
		log.info("ConversationRelay setup callSid={} from={} sessionId={}",
				inbound.callSid(), inbound.from(), inbound.sessionId());
	}

	private void handlePrompt(WebSocketSession session, RelayInboundMessage inbound) {
		if (!Boolean.TRUE.equals(inbound.last())) {
			RelaySessionState.beginSttTiming(session);
			return;
		}
		if (!StringUtils.hasText(inbound.voicePrompt())) {
			return;
		}
		String callSid = (String) session.getAttributes().get(RelaySessionAttributes.CALL_SID);
		String from = (String) session.getAttributes().get(RelaySessionAttributes.FROM);
		String utterance = inbound.voicePrompt().trim();
		int generation = RelaySessionState.nextGeneration(session);
		int turnId = RelaySessionState.nextTurnId(session);
		long promptReceivedEpochMs = System.currentTimeMillis();
		Long sttMs = RelaySessionState.endSttTiming(session);
		RelayTurnLatency latency = RelayTurnLatency.start(callSid, turnId, promptReceivedEpochMs, sttMs);
		RelaySessionState.setCurrentTurnLatency(session, latency);
		log.info("ConversationRelay prompt callSid={} turnId={} utterance={}", callSid, turnId, utterance);

		CompletableFuture.runAsync(
				() -> {
					try {
						voiceReplyService.streamReplyToUtterance(
								callSid,
								from,
								utterance,
								(text, last) -> {
									if (generation != RelaySessionState.currentGeneration(session)) {
										return false;
									}
									try {
										latency.markTtsHandoff();
										sendText(session, text, last);
										return true;
									}
									catch (Exception ex) {
										log.error("ConversationRelay send failed callSid={} turnId={}", callSid, turnId, ex);
										return false;
									}
								},
								latency);
					}
					finally {
						if (generation == RelaySessionState.currentGeneration(session) && !latency.isInterrupted()) {
							latency.logTurn();
						}
						if (RelaySessionState.currentTurnLatency(session) == latency) {
							RelaySessionState.clearCurrentTurnLatency(session);
						}
					}
				},
				replyExecutor)
				.exceptionally(error -> {
					log.error("ConversationRelay reply failed callSid={} turnId={}", callSid, turnId, error);
					latency.logTurn();
					return null;
				});
	}

	private void handleInterrupt(WebSocketSession session, RelayInboundMessage inbound) {
		RelaySessionState.invalidateInFlight(session);
		RelayTurnLatency latency = RelaySessionState.currentTurnLatency(session);
		if (latency != null) {
			latency.markInterrupted();
			latency.logTurn();
		}
		String callSid = (String) session.getAttributes().get(RelaySessionAttributes.CALL_SID);
		log.info(
				"ConversationRelay interrupt callSid={} utteranceUntilInterrupt={} durationUntilInterruptMs={} interruptedTurnId={}",
				callSid,
				inbound.utteranceUntilInterrupt(),
				inbound.durationUntilInterruptMs(),
				latency != null ? latency.turnId() : null);
	}

	private void sendText(WebSocketSession session, String spokenText, boolean last) throws Exception {
		RelayOutboundMessage outbound = RelayOutboundMessage.text(spokenText, last);
		synchronized (session) {
			if (session.isOpen()) {
				session.sendMessage(new TextMessage(objectMapper.writeValueAsString(outbound)));
			}
		}
	}
}
