package com.pokepitchshop.parley.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolTurnDetectorTest {

	private final ToolTurnDetector detector = new ToolTurnDetector();

	@Test
	void detectsBookingRequests() {
		assertThat(detector.looksLikeToolAction("Can you book an appointment for tomorrow?")).isTrue();
	}

	@Test
	void ignoresGeneralShopQuestions() {
		assertThat(detector.looksLikeToolAction("What cards do you sell?")).isFalse();
	}

}
