package com.pokepitchshop.parley.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "twilio")
public record TwilioProperties(
		@DefaultValue("") String accountSid,
		@DefaultValue("") String authToken) {

	public boolean isSignatureValidationEnabled() {
		return authToken != null && !authToken.isBlank();
	}
}
