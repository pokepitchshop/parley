package com.pokepitchshop.parley.relay;

import java.util.concurrent.atomic.AtomicBoolean;

import com.pokepitchshop.parley.voice.TurnLatencyTracker;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RelayTurnLatency implements TurnLatencyTracker {

	private final String callSid;

	private final int turnId;

	private final long promptReceivedEpochMs;

	private final Long sttMs;

	private volatile Long llmFirstTokenMs;

	private volatile Long llmCompleteMs;

	private volatile Long ttsHandoffMs;

	private volatile boolean interrupted;

	private final AtomicBoolean logged = new AtomicBoolean();

	RelayTurnLatency(String callSid, int turnId, long promptReceivedEpochMs, Long sttMs) {
		this.callSid = callSid;
		this.turnId = turnId;
		this.promptReceivedEpochMs = promptReceivedEpochMs;
		this.sttMs = sttMs;
	}

	static RelayTurnLatency start(String callSid, int turnId, long promptReceivedEpochMs, Long sttMs) {
		return new RelayTurnLatency(callSid, turnId, promptReceivedEpochMs, sttMs);
	}

	@Override
	public void markLlmFirstToken() {
		if (llmFirstTokenMs == null) {
			llmFirstTokenMs = elapsedSincePromptMs();
		}
	}

	@Override
	public void markLlmComplete() {
		llmCompleteMs = elapsedSincePromptMs();
	}

	void markTtsHandoff() {
		if (ttsHandoffMs == null) {
			ttsHandoffMs = elapsedSincePromptMs();
		}
	}

	void markInterrupted() {
		interrupted = true;
	}

	boolean isInterrupted() {
		return interrupted;
	}

	int turnId() {
		return turnId;
	}

	void logTurn() {
		if (!logged.compareAndSet(false, true)) {
			return;
		}
		log.info(
				"relay.turn.latency callSid={} turnId={} sttMs={} llmFirstTokenMs={} llmCompleteMs={} ttsHandoffMs={} interrupted={}",
				callSid,
				turnId,
				sttMs,
				llmFirstTokenMs,
				llmCompleteMs,
				ttsHandoffMs,
				interrupted);
	}

	private long elapsedSincePromptMs() {
		return System.currentTimeMillis() - promptReceivedEpochMs;
	}
}
