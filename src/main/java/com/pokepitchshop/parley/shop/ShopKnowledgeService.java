package com.pokepitchshop.parley.shop;

import org.springframework.stereotype.Service;

@Service
public class ShopKnowledgeService {

	private final StoreFacts storeFacts;

	public ShopKnowledgeService(ShopProperties shopProperties) {
		this.storeFacts = StoreFacts.from(shopProperties);
	}

	public String storeFactsSnippet() {
		return """
				You represent %s, an eBay seller trading as %s. \
				%s %s %s \
				If asked what's new and you do not have live inventory details, \
				say new cards are listed on eBay and offer to help them find something specific.
				""".formatted(
				storeFacts.storeName(),
				storeFacts.ebaySellerName(),
				storeFacts.shippingBlurb(),
				storeFacts.pickupBlurb(),
				storeFacts.toneHint());
	}

	public String shopSnippet() {
		return storeFactsSnippet();
	}
}
