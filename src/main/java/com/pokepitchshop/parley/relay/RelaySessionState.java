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

	static int nextGeneration(WebSocketSession session) {
		return generationCounter(session).incrementAndGet();
	}

	static int currentGeneration(WebSocketSession session) {
		return generationCounter(session).get();
	}

	static void invalidateInFlight(WebSocketSession session) {
		generationCounter(session).incrementAndGet();
	}
}
