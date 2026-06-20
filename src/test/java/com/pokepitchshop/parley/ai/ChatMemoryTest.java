package com.pokepitchshop.parley.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

class ChatMemoryTest {

	@Test
	void retainsMessagesPerCallSid() {
		ChatMemory memory = MessageWindowChatMemory.builder().build();
		String callSid = "CA111";

		memory.add(callSid, new UserMessage("My name is Tom."));
		memory.add(callSid, new AssistantMessage("Nice to meet you, Tom."));

		assertThat(memory.get(callSid)).hasSize(2);
		assertThat(memory.get("CA222")).isEmpty();
	}

	@Test
	void twoTurnExchangeAccumulatesHistory() {
		ChatMemory memory = MessageWindowChatMemory.builder().build();
		String callSid = "CA333";

		memory.add(callSid, new UserMessage("Remember the code is seven four two."));
		memory.add(callSid, new AssistantMessage("Got it, the code is seven four two."));
		memory.add(callSid, new UserMessage("What was the code?"));
		memory.add(callSid, new AssistantMessage("The code is seven four two."));

		assertThat(memory.get(callSid)).hasSize(4);
	}

}
