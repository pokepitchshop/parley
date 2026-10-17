package com.pokepitchshop.parley.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentGuardrailsTest {

	@Test
	void voiceSystemPromptDoesNotClaimCapabilitiesWithoutData() {
		assertThat(AgentGuardrails.VOICE_SYSTEM_PROMPT)
				.doesNotContain("store hours")
				.doesNotContain("pricing")
				.doesNotContain("orders, pickups");
	}

	@Test
	void voiceSystemPromptForbidsGuessingMissingFacts() {
		assertThat(AgentGuardrails.VOICE_SYSTEM_PROMPT)
				.contains("Never guess hours, prices, stock, or order status.")
				.contains("You cannot look up orders");
	}

	@Test
	void offTopicDeclineOnlyOffersSupportedHelp() {
		assertThat(AgentGuardrails.OFF_TOPIC_DECLINE)
				.doesNotContain("hours")
				.doesNotContain("orders");
	}
}
