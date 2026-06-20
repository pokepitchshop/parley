package com.pokepitchshop.parley.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutOfScopeDetectorTest {

	private final OutOfScopeDetector detector = new OutOfScopeDetector();

	@Test
	void detectsWeatherAsOffTopic() {
		assertThat(detector.cannedDecline("What's the weather like today?")).isPresent();
	}

	@Test
	void allowsShopQuestions() {
		assertThat(detector.cannedDecline("What are your hours?")).isEmpty();
	}

	@Test
	void detectsJokeRequests() {
		assertThat(detector.cannedDecline("Tell me a joke")).isPresent();
	}

}
