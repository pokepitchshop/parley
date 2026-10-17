package com.pokepitchshop.parley.ai;

import java.util.function.Supplier;

import org.springframework.ai.model.openai.autoconfigure.AbstractOpenAiProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;

import com.openai.credential.BearerTokenCredential;

/**
 * Gives Spring AI's OpenAI properties a keyless Azure credential before the clients are built.
 * Without one, Spring AI falls back to {@code AuthenticationUtil.getBearerTokenSupplier}, which
 * sends a real request to example.com on every LLM call just to read back its auth header.
 */
class AzureOpenAiCredentialPostProcessor implements BeanPostProcessor {

	private final Supplier<String> tokenSupplier;

	AzureOpenAiCredentialPostProcessor(Supplier<String> tokenSupplier) {
		this.tokenSupplier = tokenSupplier;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		if (bean instanceof AbstractOpenAiProperties properties && properties.getCredential() == null) {
			properties.setCredential(BearerTokenCredential.create(tokenSupplier));
		}
		return bean;
	}

}
