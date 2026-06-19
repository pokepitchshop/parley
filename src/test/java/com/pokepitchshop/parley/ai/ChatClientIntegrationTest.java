package com.pokepitchshop.parley.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ChatClientIntegrationTest {

	@Autowired
	private ChatClient chatClient;

	@Test
	void returnsRealCompletion() {
		String reply = chatClient.prompt().user("What is two plus two?").call().content();

		assertThat(reply).isNotBlank();
	}

}
