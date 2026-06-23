package com.pokepitchshop.parley.relay;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ConversationRelayHandler extends TextWebSocketHandler {

	static final String ECHO_PREFIX = "You said: ";

	private final ObjectMapper objectMapper = new ObjectMapper();

	public ConversationRelayHandler() {
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
		String callSid = (String) session.getAttributes().get(RelaySessionAttributes.CALL_SID);
		log.info("ConversationRelay WebSocket closed callSid={} sessionId={} status={}",
				callSid, session.getId(), status);
	}

	private void handleSetup(WebSocketSession session, RelayInboundMessage inbound) {
		session.getAttributes().put(RelaySessionAttributes.CALL_SID, inbound.callSid());
		session.getAttributes().put(RelaySessionAttributes.FROM, inbound.from());
		session.getAttributes().put(RelaySessionAttributes.SESSION_ID, inbound.sessionId());
		log.info("ConversationRelay setup callSid={} from={} sessionId={}",
				inbound.callSid(), inbound.from(), inbound.sessionId());
	}

	private void handlePrompt(WebSocketSession session, RelayInboundMessage inbound) throws Exception {
		if (!Boolean.TRUE.equals(inbound.last())) {
			return;
		}
		if (!StringUtils.hasText(inbound.voicePrompt())) {
			return;
		}
		String callSid = (String) session.getAttributes().get(RelaySessionAttributes.CALL_SID);
		log.info("ConversationRelay prompt callSid={} utterance={}", callSid, inbound.voicePrompt());
		sendText(session, ECHO_PREFIX + inbound.voicePrompt().trim());
	}

	private void handleInterrupt(WebSocketSession session, RelayInboundMessage inbound) {
		String callSid = (String) session.getAttributes().get(RelaySessionAttributes.CALL_SID);
		log.info("ConversationRelay interrupt callSid={} utteranceUntilInterrupt={} durationMs={}",
				callSid, inbound.utteranceUntilInterrupt(), inbound.durationUntilInterruptMs());
	}

	private void sendText(WebSocketSession session, String spokenText) throws Exception {
		RelayOutboundMessage outbound = RelayOutboundMessage.text(spokenText, true);
		session.sendMessage(new TextMessage(objectMapper.writeValueAsString(outbound)));
	}
}
