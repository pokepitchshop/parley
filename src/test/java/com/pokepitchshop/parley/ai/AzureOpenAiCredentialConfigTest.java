package com.pokepitchshop.parley.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AzureOpenAiCredentialConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(AzureOpenAiCredentialConfig.class);

	@Test
	void azureProfileRegistersCachedCredential() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("azure"))
				.run(context -> assertThat(context).hasSingleBean(AzureOpenAiCredentialPostProcessor.class));
	}

	@Test
	void localProfileKeepsApiKeyAuth() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
				.run(context -> assertThat(context).doesNotHaveBean(AzureOpenAiCredentialPostProcessor.class));
	}

}
