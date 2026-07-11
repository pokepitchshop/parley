package com.pokepitchshop.parley.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokepitchshop.parley.caller.CallerContext;
import com.pokepitchshop.parley.caller.CallerService;

@ExtendWith(MockitoExtension.class)
class TurnContextServiceTest {

	private static final String PHONE = "+15551234567";

	@Mock
	private CallerService callerService;

	@Mock
	private ShopKnowledgeService shopKnowledgeService;

	private TurnContextService sut;

	@BeforeEach
	void setUp() {
		sut = new TurnContextService(callerService, shopKnowledgeService);
	}

	@Test
	void forCallerCombinesCallerAndShopSnippets() {
		CallerContext callerContext = CallerContext.anonymous();
		given(callerService.contextFor(PHONE)).willReturn(callerContext);
		given(shopKnowledgeService.shopSnippet()).willReturn("Shop facts here.");

		TurnContext context = sut.forCaller(PHONE);

		assertThat(context.callerSnippet()).isEqualTo(callerContext.systemPromptSnippet());
		assertThat(context.shopSnippet()).isEqualTo("Shop facts here.");
	}
}
