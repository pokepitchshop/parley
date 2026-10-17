package com.pokepitchshop.parley.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pokepitchshop.parley.inventory.EbayActiveListingsCsvParser.ParsedListings;
import com.pokepitchshop.parley.inventory.EbayActiveListingsCsvParser.SkippedRow;

class EbayActiveListingsCsvParserTest {

	private static final Instant IMPORTED_AT = Instant.parse("2026-10-17T05:00:00Z");

	private EbayActiveListingsCsvParser sut;

	@BeforeEach
	void setUp() {
		sut = new EbayActiveListingsCsvParser();
	}

	@Test
	void mapsSellerHubColumnsByHeaderName() throws IOException {
		ParsedListings parsed = parseFixture("active-listings-sample.csv");

		InventoryItem griffey = parsed.items().get(0);
		assertThat(griffey.getId()).isEqualTo("100000000001");
		assertThat(griffey.getTitle()).isEqualTo("1989 UD #1 Griffey Jr RC, PSA 9");
		assertThat(griffey.getSku()).isEqualTo("SPT-01-001");
		assertThat(griffey.getCategory()).isEqualTo("Trading Card Singles");
		assertThat(griffey.getPrice()).isEqualByComparingTo(new BigDecimal("189.99"));
		assertThat(griffey.getQuantity()).isEqualTo(1);
		assertThat(griffey.getStatus()).isEqualTo(InventoryItem.STATUS_ACTIVE);
		assertThat(griffey.getImportedAt()).isEqualTo(IMPORTED_AT);
	}

	@Test
	void keepsQuotedCommasAndQuotesInTitles() throws IOException {
		ParsedListings parsed = parseFixture("active-listings-sample.csv");

		assertThat(parsed.items()).extracting(InventoryItem::getTitle)
				.contains("Sol Ring U Commander: Foundations 2 NM \"Classic\"");
	}

	@Test
	void conditionUsesGraderAndGradeForSlabsAndCardConditionOtherwise() throws IOException {
		ParsedListings parsed = parseFixture("active-listings-sample.csv");

		assertThat(parsed.items()).extracting(InventoryItem::getId, InventoryItem::getCondition).containsExactly(
				org.assertj.core.groups.Tuple.tuple("100000000001", "PSA 9"),
				org.assertj.core.groups.Tuple.tuple("100000000002", "Near mint or better"),
				org.assertj.core.groups.Tuple.tuple("100000000005", "Lightly played (Excellent)"));
	}

	@Test
	void skipsAndReportsRowsWithoutItemNumberOrWithUnparseablePrice() throws IOException {
		ParsedListings parsed = parseFixture("active-listings-sample.csv");

		assertThat(parsed.items()).hasSize(3);
		assertThat(parsed.skipped()).containsExactly(
				new SkippedRow(4, "missing item number"),
				new SkippedRow(5, "unparseable price 'abc'"));
	}

	@Test
	void mapsByHeaderWhenColumnsAreReordered() throws IOException {
		ParsedListings parsed = parseFixture("active-listings-reordered.csv");

		assertThat(parsed.items()).singleElement().satisfies(item -> {
			assertThat(item.getId()).isEqualTo("200000000001");
			assertThat(item.getTitle()).isEqualTo("1990 Topps Griffey Jr #336");
			assertThat(item.getPrice()).isEqualByComparingTo(new BigDecimal("24.50"));
			assertThat(item.getQuantity()).isEqualTo(2);
			assertThat(item.getCondition()).isEqualTo("VERY_GOOD");
		});
	}

	@Test
	void rejectsAFileMissingRequiredColumns() {
		Reader csv = new StringReader("Item number,Title\n1,Card\n");

		assertThatThrownBy(() -> sut.parse(csv, IMPORTED_AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Current price")
				.hasMessageContaining("Available quantity");
	}

	private ParsedListings parseFixture(String name) throws IOException {
		try (Reader reader = new InputStreamReader(
				Objects.requireNonNull(getClass().getResourceAsStream("/inventory/" + name)),
				StandardCharsets.UTF_8)) {
			return sut.parse(reader, IMPORTED_AT);
		}
	}

}
