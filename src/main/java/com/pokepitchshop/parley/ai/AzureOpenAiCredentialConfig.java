package com.pokepitchshop.parley.ai;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.azure.identity.DefaultAzureCredentialBuilder;

/**
 * Keyless Azure OpenAI auth on Container Apps with a cached token, instead of Spring AI's default
 * supplier that makes an extra HTTP round trip (and can fail on a stale connection) every turn.
 */
@Configuration
@Profile("azure")
public class AzureOpenAiCredentialConfig {

	@Bean
	static AzureOpenAiCredentialPostProcessor azureOpenAiCredentialPostProcessor() {
		return new AzureOpenAiCredentialPostProcessor(
				new CachedAzureTokenSupplier(new DefaultAzureCredentialBuilder().build(), Clock.systemUTC()));
	}

}
