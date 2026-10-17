package com.pokepitchshop.parley.shop;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Minimal "is the caller asking what we have?" check for POK-482, so non-shopping turns carry no
 * inventory text. Replace with POK-449's shopping-intent detection once that lands.
 */
final class ShoppingIntent {

	private static final Pattern SHOPPING = Pattern.compile(
			"\\b(do you have|you have|have any|got any|you got|carry|in stock|stock|selling|sell|for sale|"
					+ "looking for|buy|price|cost|how much|available|cards?|rookies?|rc|graded|psa|bgs|cgc|slab|"
					+ "packs?|boxes|singles?|holo|pokemon|mtg|magic)\\b");

	private ShoppingIntent() {
	}

	static boolean looksLikeShopping(String utterance) {
		return utterance != null && SHOPPING.matcher(utterance.toLowerCase(Locale.ROOT)).find();
	}
}
