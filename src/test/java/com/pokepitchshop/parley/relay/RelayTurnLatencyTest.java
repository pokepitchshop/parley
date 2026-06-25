package com.pokepitchshop.parley.relay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RelayTurnLatencyTest {

	@Test
	void recordsStageTimingsRelativeToPromptReceived() {
		var latency = RelayTurnLatency.start("CA123", 1, System.currentTimeMillis() - 500, 120L);

		latency.markLlmFirstToken();
		latency.markLlmComplete();
		latency.markTtsHandoff();

		assertThat(latency.turnId()).isEqualTo(1);
		assertThat(latency.isInterrupted()).isFalse();
	}

	@Test
	void logsTurnOnceEvenWhenCalledMultipleTimes() {
		var latency = RelayTurnLatency.start("CA123", 2, System.currentTimeMillis(), null);

		latency.logTurn();
		latency.markInterrupted();
		latency.logTurn();

		assertThat(latency.isInterrupted()).isTrue();
	}
}
