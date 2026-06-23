package com.pokepitchshop.parley.relay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RelayInboundMessage(
		String type,
		@JsonProperty("sessionId") String sessionId,
		@JsonProperty("callSid") String callSid,
		@JsonProperty("from") String from,
		@JsonProperty("to") String to,
		@JsonProperty("voicePrompt") String voicePrompt,
		Boolean last,
		@JsonProperty("utteranceUntilInterrupt") String utteranceUntilInterrupt,
		@JsonProperty("durationUntilInterruptMs") Integer durationUntilInterruptMs,
		String description) {
}
