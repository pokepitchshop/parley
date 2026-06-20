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
			You are Parley, the phone assistant for Poke Pitch Shop. \
			Answer in one or two short, conversational sentences, like you are on a live call. \
			Use what the caller said earlier in this same call when they refer back to it. \
			Never use markdown, lists, or URLs.
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
