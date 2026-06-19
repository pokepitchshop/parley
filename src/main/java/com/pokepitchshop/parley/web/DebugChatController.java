package com.pokepitchshop.parley.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
public class DebugChatController {

	private final ChatClient chatClient;

	public DebugChatController(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	@GetMapping(value = "/debug/chat", produces = "text/plain")
	String chat(@RequestParam(defaultValue = "Hello") String q) {
		return chatClient.prompt().user(q).call().content();
	}

}
