package com.pokepitchshop.parley.relay;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.WebSocketSession;

final class RelaySessionState {

	private RelaySessionState() {
	}

	static AtomicInteger generationCounter(WebSocketSession session) {
		return (AtomicInteger) session.getAttributes()
				.computeIfAbsent(RelaySessionAttributes.GENERATION, key -> new AtomicInteger(0));
	}

	static AtomicInteger turnCounter(WebSocketSession session) {
		return (AtomicInteger) session.getAttributes()
				.computeIfAbsent(RelaySessionAttributes.TURN_ID, key -> new AtomicInteger(0));
	}

	static int nextGeneration(WebSocketSession session) {
		return generationCounter(session).incrementAndGet();
	}

	static int currentGeneration(WebSocketSession session) {
		return generationCounter(session).get();
	}

	static void invalidateInFlight(WebSocketSession session) {
		generationCounter(session).incrementAndGet();
	}

	static int nextTurnId(WebSocketSession session) {
		return turnCounter(session).incrementAndGet();
	}

	static void beginSttTiming(WebSocketSession session) {
		session.getAttributes().putIfAbsent(RelaySessionAttributes.STT_STARTED_AT, System.currentTimeMillis());
	}

	static Long endSttTiming(WebSocketSession session) {
		Long startedAt = (Long) session.getAttributes().remove(RelaySessionAttributes.STT_STARTED_AT);
		if (startedAt == null) {
			return null;
		}
		return System.currentTimeMillis() - startedAt;
	}

	static RelayTurnLatency currentTurnLatency(WebSocketSession session) {
		return (RelayTurnLatency) session.getAttributes().get(RelaySessionAttributes.CURRENT_TURN_LATENCY);
	}

	static void setCurrentTurnLatency(WebSocketSession session, RelayTurnLatency latency) {
		session.getAttributes().put(RelaySessionAttributes.CURRENT_TURN_LATENCY, latency);
	}

	static void clearCurrentTurnLatency(WebSocketSession session) {
		session.getAttributes().remove(RelaySessionAttributes.CURRENT_TURN_LATENCY);
	}
}
