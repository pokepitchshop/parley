package com.pokepitchshop.parley.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DebugChatController.class)
@ActiveProfiles("local")
class DebugChatControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChatClient chatClient;

	@MockitoBean
	private ChatClient.ChatClientRequestSpec requestSpec;

	@MockitoBean
	private ChatClient.CallResponseSpec responseSpec;

	@Test
	void chatReturnsCompletion() throws Exception {
		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.user(anyString())).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(responseSpec);
		given(responseSpec.content()).willReturn("Four.");

		mockMvc.perform(get("/debug/chat").param("q", "What is two plus two?"))
				.andExpect(status().isOk())
				.andExpect(content().string("Four."));
	}

}
