package com.pokepitchshop.parley.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
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
	ChatMemory chatMemory() {
		return MessageWindowChatMemory.builder().build();
	}

	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
		return builder
				.defaultSystem(SYSTEM_PROMPT)
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
	}

}
