package com.pokepitchshop.parley.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.pokepitchshop.parley.caller.CallerContext;
import com.pokepitchshop.parley.caller.CallerService;
import com.pokepitchshop.parley.inventory.InventoryIndex;
import com.pokepitchshop.parley.inventory.InventoryItem;
import com.pokepitchshop.parley.inventory.InventoryItemRepository;
import com.pokepitchshop.parley.inventory.InventoryMeta;
import com.pokepitchshop.parley.inventory.InventoryMetaRepository;
import com.pokepitchshop.parley.inventory.InventoryProperties;

@ExtendWith(MockitoExtension.class)
class TurnContextServiceTest {

	private static final String PHONE = "+15551234567";

	private static final Instant IMPORTED_AT = Instant.parse("2026-10-16T17:00:00Z");

	@Mock
	private CallerService callerService;

	@Mock
	private ShopKnowledgeService shopKnowledgeService;

	@Mock
	private InventoryItemRepository itemRepository;

	@Mock
	private InventoryMetaRepository metaRepository;

	private InventoryIndex inventoryIndex;

	private TurnContextService sut;

	@BeforeEach
	void setUp() {
		inventoryIndex = new InventoryIndex(itemRepository, metaRepository,
				new InventoryProperties(Duration.ofMinutes(5), 3, 1, List.of("jr|junior", "rookie|rc")));
		sut = new TurnContextService(callerService, shopKnowledgeService, inventoryIndex);
		given(callerService.contextFor(PHONE)).willReturn(CallerContext.anonymous());
		given(shopKnowledgeService.shopSnippet()).willReturn("Shop facts here.");
	}

	@Test
	void forCallerCombinesCallerAndShopSnippets() {
		TurnContext context = sut.forCaller(PHONE, "What are your hours?");

		assertThat(context.callerSnippet()).isEqualTo(CallerContext.anonymous().systemPromptSnippet());
		assertThat(context.shopSnippet()).isEqualTo("Shop facts here.");
	}

	@Test
	void stockQuestionForListedCardCarriesItsRealPriceAndQuantity() {
		given(shopKnowledgeService.ebaySellerName()).willReturn("tnt4sportscards");
		loadIndex(
				item("1", "1989 Upper Deck Ken Griffey Jr. #1 Rookie RC PSA 9", "PSA 9", "189.99", 2),
				item("2", "2023 Topps Chrome Julio Rodriguez Refractor", null, "12.50", 1));

		TurnContext context = sut.forCaller(PHONE, "Do you have any Ken Griffey Junior rookies?");

		assertThat(context.inventorySnippet())
				.startsWith("Inventory (eBay store tnt4sportscards, as of October 16, 2026):")
				.contains("1) 1989 Upper Deck Ken Griffey Jr. #1 Rookie RC PSA 9 (PSA 9) — $189.99, 2 available.")
				.doesNotContain("Julio");
	}

	@Test
	void stockQuestionForUnlistedCardGetsNoMatchInstructionWithoutItems() {
		loadIndex(item("1", "1989 Upper Deck Ken Griffey Jr. #1 Rookie RC PSA 9", "PSA 9", "189.99", 2));

		TurnContext context = sut.forCaller(PHONE, "Do you have any Black Lotus?");

		assertThat(context.inventorySnippet())
				.isEqualTo(TurnContextService.NO_MATCHING_LISTINGS)
				.doesNotContain("Griffey");
	}

	@Test
	void nonShoppingTurnCarriesNoInventoryText() {
		loadIndex(item("1", "1989 Upper Deck Ken Griffey Jr. #1 Rookie RC PSA 9", "PSA 9", "189.99", 2));

		assertThat(sut.forCaller(PHONE, "What are your hours?").inventorySnippet()).isEmpty();
		assertThat(sut.forCaller(PHONE, "Do you ship to Canada?").inventorySnippet()).isEmpty();
		assertThat(sut.forCaller(PHONE, "Thanks, have a good one").inventorySnippet()).isEmpty();
	}

	@Test
	void emptyIndexBehavesLikeNoMatch() {
		given(metaRepository.findById(InventoryMeta.SINGLETON_ID))
				.willThrow(new DataAccessResourceFailureException("mongo down"));
		inventoryIndex.refreshIfChanged();

		TurnContext context = sut.forCaller(PHONE, "Do you have any Ken Griffey Jr rookies?");

		assertThat(context.inventorySnippet()).isEqualTo(TurnContextService.NO_MATCHING_LISTINGS);
	}

	private void loadIndex(InventoryItem... items) {
		given(metaRepository.findById(InventoryMeta.SINGLETON_ID))
				.willReturn(Optional.of(new InventoryMeta(InventoryMeta.SINGLETON_ID, IMPORTED_AT, items.length, "test.csv")));
		given(itemRepository.findByStatus(InventoryItem.STATUS_ACTIVE)).willReturn(List.of(items));
		inventoryIndex.refreshIfChanged();
	}

	private static InventoryItem item(String id, String title, String condition, String price, int quantity) {
		return new InventoryItem(id, title, null, null, new BigDecimal(price), quantity, condition,
				InventoryItem.STATUS_ACTIVE, IMPORTED_AT);
	}
}
