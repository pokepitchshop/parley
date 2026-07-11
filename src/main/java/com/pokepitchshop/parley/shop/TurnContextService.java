package com.pokepitchshop.parley.shop;

import org.springframework.stereotype.Service;

import com.pokepitchshop.parley.caller.CallerService;

@Service
public class TurnContextService {

	private final CallerService callerService;

	private final ShopKnowledgeService shopKnowledgeService;

	public TurnContextService(CallerService callerService, ShopKnowledgeService shopKnowledgeService) {
		this.callerService = callerService;
		this.shopKnowledgeService = shopKnowledgeService;
	}

	public TurnContext forCaller(String fromNumber) {
		return new TurnContext(
				callerService.contextFor(fromNumber).systemPromptSnippet(),
				shopKnowledgeService.shopSnippet());
	}
}
