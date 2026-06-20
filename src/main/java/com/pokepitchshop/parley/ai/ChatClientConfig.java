package com.pokepitchshop.parley.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pokepitchshop.parley.guardrails.AgentGuardrails;

@Configuration
public class ChatClientConfig {

	private static final String SYSTEM_PROMPT = AgentGuardrails.VOICE_SYSTEM_PROMPT;

	static final String SUMMARY_SYSTEM_PROMPT = """
			You summarize completed phone calls for Poke Pitch Shop operators. \
			In two to four short plain sentences, cover caller intent, outcome, any actions taken, and follow-ups needed. \
			Never use markdown, lists, or bullet points.
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

	@Bean
	@Qualifier("summaryChatClient")
	ChatClient summaryChatClient(ChatModel chatModel) {
		return ChatClient.builder(chatModel)
				.defaultSystem(SUMMARY_SYSTEM_PROMPT)
				.build();
	}

}
