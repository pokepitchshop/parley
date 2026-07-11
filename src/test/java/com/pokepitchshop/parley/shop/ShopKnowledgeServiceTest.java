package com.pokepitchshop.parley.shop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShopKnowledgeServiceTest {

	private ShopKnowledgeService sut;

	@BeforeEach
	void setUp() {
		sut = new ShopKnowledgeService(new ShopProperties(
				"Poke Pitch Shop",
				"PokePitchShop",
				"We ship cards nationwide via eBay.",
				"Pickup available by arrangement.",
				"Friendly collector tone."));
	}

	@Test
	void storeFactsSnippetIncludesStoreAndEbayDetails() {
		String snippet = sut.storeFactsSnippet();

		assertThat(snippet).contains("Poke Pitch Shop");
		assertThat(snippet).contains("PokePitchShop");
		assertThat(snippet).contains("We ship cards nationwide via eBay.");
		assertThat(snippet).contains("Pickup available by arrangement.");
		assertThat(snippet).contains("Friendly collector tone.");
	}

	@Test
	void shopSnippetDelegatesToStoreFactsForPhaseZero() {
		assertThat(sut.shopSnippet()).isEqualTo(sut.storeFactsSnippet());
	}
}
