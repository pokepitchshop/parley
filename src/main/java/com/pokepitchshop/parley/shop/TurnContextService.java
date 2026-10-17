package com.pokepitchshop.parley.shop;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.pokepitchshop.parley.caller.CallerService;
import com.pokepitchshop.parley.inventory.InventoryIndex;
import com.pokepitchshop.parley.inventory.InventoryItem;

@Service
public class TurnContextService {

	static final String NO_MATCHING_LISTINGS = "No matching listings right now. Do not guess stock or prices; "
			+ "offer to have someone check, or suggest browsing the eBay store.";

	/** Shared title tokens needed to inject listings on a turn without shopping words. */
	static final int STRONG_MATCH_SCORE = 2;

	private static final DateTimeFormatter AS_OF = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
			.withZone(ZoneId.of("America/Chicago"));

	private final CallerService callerService;

	private final ShopKnowledgeService shopKnowledgeService;

	private final InventoryIndex inventoryIndex;

	public TurnContextService(
			CallerService callerService,
			ShopKnowledgeService shopKnowledgeService,
			InventoryIndex inventoryIndex) {
		this.callerService = callerService;
		this.shopKnowledgeService = shopKnowledgeService;
		this.inventoryIndex = inventoryIndex;
	}

	public TurnContext forCaller(String fromNumber, String utterance) {
		return new TurnContext(
				callerService.contextFor(fromNumber).systemPromptSnippet(),
				shopKnowledgeService.shopSnippet(),
				inventorySnippet(utterance));
	}

	private String inventorySnippet(String utterance) {
		List<InventoryItem> matches;
		if (ShoppingIntent.looksLikeShopping(utterance)) {
			matches = inventoryIndex.match(utterance);
			if (matches.isEmpty()) {
				return NO_MATCHING_LISTINGS;
			}
		}
		else {
			// Callers often name a card with no shopping words ("What about an ice tunnel?").
			matches = inventoryIndex.match(utterance, STRONG_MATCH_SCORE);
			if (matches.isEmpty()) {
				return "";
			}
		}
		// Matches only exist after a successful load, and every load records lastImportAt.
		var snippet = new StringBuilder("Inventory (eBay store %s, as of %s):"
				.formatted(shopKnowledgeService.ebaySellerName(), AS_OF.format(inventoryIndex.lastImportAt().orElseThrow())));
		for (int i = 0; i < matches.size(); i++) {
			snippet.append(' ').append(i + 1).append(") ").append(describe(matches.get(i)));
		}
		return snippet.toString();
	}

	private static String describe(InventoryItem item) {
		String condition = StringUtils.hasText(item.getCondition()) ? " (" + item.getCondition() + ")" : "";
		return "%s%s — $%s, %d available.".formatted(item.getTitle(), condition, item.getPrice(), item.getQuantity());
	}
}
