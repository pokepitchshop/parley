package com.pokepitchshop.parley.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

	private static final String SYSTEM_PROMPT = """
			You are Parley, a voice agent for Poke Pitch Shop. \
			Reply in one or two short, spoken-style sentences. \
			No markdown, lists, or URLs read aloud.
			""";

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder
				.defaultSystem(SYSTEM_PROMPT)
				.build();
	}

}
