package com.pokepitchshop.parley.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "parley.voice")
public record VoiceProperties(
		@DefaultValue("POLLY_JOANNA_NEURAL") String sayVoice,
		@DefaultValue("3") int speechTimeout,
		@DefaultValue("25") int maxTurnsPerCall,
		@DefaultValue("5") int maxToolCallsPerCall,
		@DefaultValue("relay") String mode) {

	public boolean isTurnBased() {
		return "turn".equalsIgnoreCase(mode);
	}
}
