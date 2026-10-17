package com.pokepitchshop.parley.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class InventoryIndexTest {

	private static final Instant IMPORTED_AT = Instant.parse("2026-10-16T12:00:00Z");

	private static final List<String> CARD_SPEAK = List.of(
			"jr|junior",
			"rookie|rc",
			"upper deck|ud",
			"mtg|magic the gathering",
			"psa 10|psa ten",
			"charizard|zard");

	@Mock
	private InventoryItemRepository itemRepository;

	@Mock
	private InventoryMetaRepository metaRepository;

	private InventoryIndex sut;

	@BeforeEach
	void setUp() {
		sut = new InventoryIndex(itemRepository, metaRepository,
				new InventoryProperties(Duration.ofMinutes(5), 3, 1, CARD_SPEAK));
	}

	@Test
	void matchFindsGriffeyRookieFromCardSpeakTitle() {
		InventoryItem griffey = item("1", "1989 UD #1 Griffey Jr RC PSA 9", 1);
		loadIndex(griffey, item("2", "Seething Song C 9ED #216 NM MTG Magic", 3));

		List<InventoryItem> matches = sut.match("do you have any Griffey rookies");

		assertThat(matches).extracting(InventoryItem::getId).containsExactly("1");
	}

	@Test
	void questionWithNoShoppingContentReturnsNoMatches() {
		loadIndex(item("1", "1989 UD #1 Griffey Jr RC PSA 9", 1),
				item("2", "What Are Your Plans R GPT #40 NM MTG Magic", 2));

		assertThat(sut.match("what are your hours")).isEmpty();
	}

	@Test
	void endedItemsAreNeverReturned() {
		InventoryItem ended = item("2", "1989 UD #1 Griffey Jr RC PSA 10", 5);
		ended.setStatus(InventoryItem.STATUS_ENDED);
		loadIndex(item("1", "1989 UD #1 Griffey Jr RC PSA 9", 1), ended);

		assertThat(sut.match("griffey")).extracting(InventoryItem::getId).containsExactly("1");
	}

	@Test
	void startsEmptyWithoutFailingWhenMongoIsUnreachable() {
		given(metaRepository.findById(InventoryMeta.SINGLETON_ID))
				.willThrow(new DataAccessResourceFailureException("mongo down"));

		sut.refreshIfChanged();

		assertThat(sut.match("griffey rookie")).isEmpty();
	}

	@Test
	void laterRefreshFillsIndexAfterStartupFailure() {
		given(metaRepository.findById(InventoryMeta.SINGLETON_ID))
				.willThrow(new DataAccessResourceFailureException("mongo down"))
				.willReturn(Optional.of(meta(IMPORTED_AT)));
		given(itemRepository.findByStatus(InventoryItem.STATUS_ACTIVE))
				.willReturn(List.of(item("1", "1989 UD #1 Griffey Jr RC PSA 9", 1)));

		sut.refreshIfChanged();
		sut.refreshIfChanged();

		assertThat(sut.match("griffey")).extracting(InventoryItem::getId).containsExactly("1");
	}

	@Test
	void failedRefreshKeepsPreviousSnapshot() {
		given(metaRepository.findById(InventoryMeta.SINGLETON_ID))
				.willReturn(Optional.of(meta(IMPORTED_AT)), Optional.of(meta(IMPORTED_AT.plusSeconds(3600))));
		given(itemRepository.findByStatus(InventoryItem.STATUS_ACTIVE))
				.willReturn(List.of(item("1", "1989 UD #1 Griffey Jr RC PSA 9", 1)))
				.willThrow(new DataAccessResourceFailureException("mongo down"));

		sut.refreshIfChanged();
		sut.refreshIfChanged();

		assertThat(sut.match("griffey")).extracting(InventoryItem::getId).containsExactly("1");
	}

	@Test
	void reloadsOnlyWhenLastImportAtChanges() {
		given(metaRepository.findById(InventoryMeta.SINGLETON_ID)).willReturn(Optional.of(meta(IMPORTED_AT)));
		given(itemRepository.findByStatus(InventoryItem.STATUS_ACTIVE))
				.willReturn(List.of(item("1", "1989 UD #1 Griffey Jr RC PSA 9", 1)))
				.willReturn(List.of(item("2", "1990 Topps Griffey Jr PSA 10", 1)));

		sut.refreshIfChanged();
		sut.refreshIfChanged();

		assertThat(sut.match("griffey")).extracting(InventoryItem::getId).containsExactly("1");
	}

	@Test
	void matchNeverQueriesMongo() {
		loadIndex(item("1", "1989 UD #1 Griffey Jr RC PSA 9", 1));
		clearInvocations(itemRepository, metaRepository);

		List<InventoryItem> matches = sut.match("griffey rookie");

		assertThat(matches).hasSize(1);
		verifyNoInteractions(itemRepository, metaRepository);
	}

	@Test
	void moreOverlappingTokensRankFirst() {
		loadIndex(item("1", "1990 Topps Griffey Jr #336", 9),
				item("2", "1989 UD #1 Griffey Jr RC PSA 10", 1));

		assertThat(sut.match("griffey rookie psa ten")).extracting(InventoryItem::getId)
				.containsExactly("2", "1");
	}

	@Test
	void tiesPreferHigherQuantityThenNewestImportAndReturnTopN() {
		Instant older = IMPORTED_AT.minusSeconds(86_400);
		loadIndex(item("low-qty", "Griffey Jr Upper Deck", 1, IMPORTED_AT),
				item("high-qty", "Griffey Jr Topps", 4, older),
				item("older", "Griffey Jr Fleer", 2, older),
				item("newer", "Griffey Jr Donruss", 2, IMPORTED_AT));

		assertThat(sut.match("griffey")).extracting(InventoryItem::getId)
				.containsExactly("high-qty", "newer", "older");
	}

	@Test
	void synonymsComeFromConfig() {
		loadIndex(item("1", "Charizard ex Obsidian Flames 223/197 NM", 1));

		assertThat(sut.match("got any zards")).extracting(InventoryItem::getId).containsExactly("1");
	}

	@Test
	void matchIsFastOnFiveThousandItems() {
		InventoryItem[] items = new InventoryItem[5_000];
		for (int i = 0; i < items.length; i++) {
			items[i] = item(String.valueOf(i), "Card " + i + " Foundations #" + (i % 300) + " NM MTG Magic Player " + (i % 97), 1 + i % 4);
		}
		items[4_321] = item("griffey", "1989 UD #1 Griffey Jr RC PSA 9", 1);
		loadIndex(items);

		for (int i = 0; i < 200; i++) {
			sut.match("do you have any griffey rookies or magic foundations");
		}
		long[] nanos = new long[1_000];
		for (int i = 0; i < nanos.length; i++) {
			long start = System.nanoTime();
			sut.match("do you have any griffey rookies or magic foundations");
			nanos[i] = System.nanoTime() - start;
		}
		Arrays.sort(nanos);

		assertThat(Duration.ofNanos(nanos[(int) (nanos.length * 0.99)])).isLessThan(Duration.ofMillis(5));
	}

	private static InventoryMeta meta(Instant lastImportAt) {
		return new InventoryMeta(InventoryMeta.SINGLETON_ID, lastImportAt, 1, "test.csv");
	}

	private void loadIndex(InventoryItem... items) {
		given(metaRepository.findById(InventoryMeta.SINGLETON_ID)).willReturn(Optional.of(meta(IMPORTED_AT)));
		given(itemRepository.findByStatus(InventoryItem.STATUS_ACTIVE)).willReturn(List.of(items));
		sut.refreshIfChanged();
	}

	private static InventoryItem item(String id, String title, int quantity) {
		return item(id, title, quantity, IMPORTED_AT);
	}

	private static InventoryItem item(String id, String title, int quantity, Instant importedAt) {
		return new InventoryItem(id, title, null, null, new BigDecimal("9.99"), quantity, null,
				InventoryItem.STATUS_ACTIVE, importedAt);
	}

}
