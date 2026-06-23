package com.pokepitchshop.parley.voice;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class PendingTurnStore {

	record PendingTurn(String fromNumber, String speech) {
	}

	private final ConcurrentHashMap<String, PendingTurn> pending = new ConcurrentHashMap<>();

	void store(String callSid, String fromNumber, String speech) {
		pending.put(callSid, new PendingTurn(fromNumber, speech));
	}

	Optional<PendingTurn> consume(String callSid) {
		return Optional.ofNullable(pending.remove(callSid));
	}

}
