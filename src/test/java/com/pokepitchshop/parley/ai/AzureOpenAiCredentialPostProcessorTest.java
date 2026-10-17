package com.pokepitchshop.parley.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;

import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;

class AzureOpenAiCredentialPostProcessorTest {

	private AzureOpenAiCredentialPostProcessor sut;

	@BeforeEach
	void setUp() {
		sut = new AzureOpenAiCredentialPostProcessor(() -> "cached-token");
	}

	@Test
	void openAiPropertiesWithoutCredentialGetCachedBearerToken() {
		var common = new OpenAiCommonProperties();
		var chat = new OpenAiChatProperties();

		sut.postProcessBeforeInitialization(common, "common");
		sut.postProcessBeforeInitialization(chat, "chat");

		assertThat(common.getCredential()).isInstanceOfSatisfying(BearerTokenCredential.class,
				credential -> assertThat(credential.token()).isEqualTo("cached-token"));
		assertThat(chat.getCredential()).isInstanceOfSatisfying(BearerTokenCredential.class,
				credential -> assertThat(credential.token()).isEqualTo("cached-token"));
	}

	@Test
	void explicitlyConfiguredCredentialIsLeftAlone() {
		Credential configured = BearerTokenCredential.create("configured");
		var common = new OpenAiCommonProperties();
		common.setCredential(configured);

		sut.postProcessBeforeInitialization(common, "common");

		assertThat(common.getCredential()).isSameAs(configured);
	}

}
