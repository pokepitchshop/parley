package com.pokepitchshop.parley.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokepitchshop.parley.inventory.EbayActiveListingsCsvParser.ParsedListings;
import com.pokepitchshop.parley.inventory.EbayActiveListingsCsvParser.SkippedRow;
import com.pokepitchshop.parley.inventory.InventoryImportService.ImportSummary;

@ExtendWith(MockitoExtension.class)
class InventoryImportServiceTest {

	private static final Instant PREVIOUS_IMPORT = Instant.parse("2026-10-10T05:00:00Z");

	private static final Instant NOW = Instant.parse("2026-10-17T05:00:00Z");

	@Mock
	private InventoryItemRepository itemRepository;

	@Mock
	private InventoryMetaRepository metaRepository;

	@Captor
	private ArgumentCaptor<List<InventoryItem>> savedItems;

	@Captor
	private ArgumentCaptor<InventoryMeta> savedMeta;

	private InventoryImportService sut;

	@BeforeEach
	void setUp() {
		sut = new InventoryImportService(itemRepository, metaRepository);
	}

	@Test
	void countsAddedUpdatedAndEndedListings() {
		given(itemRepository.findAll()).willReturn(List.of(
				stored("A", InventoryItem.STATUS_ACTIVE),
				stored("B", InventoryItem.STATUS_ACTIVE),
				stored("C", InventoryItem.STATUS_ENDED)));

		ImportSummary summary = sut.apply(parsed(imported("A"), imported("D")), "listings.csv", NOW, false);

		assertThat(summary.added()).isEqualTo(1);
		assertThat(summary.updated()).isEqualTo(1);
		assertThat(summary.ended()).isEqualTo(1);
	}

	@Test
	void listingsMissingFromTheFileAreMarkedEndedNotDeleted() {
		given(itemRepository.findAll()).willReturn(List.of(stored("A", InventoryItem.STATUS_ACTIVE),
				stored("B", InventoryItem.STATUS_ACTIVE)));

		sut.apply(parsed(imported("A")), "listings.csv", NOW, false);

		verify(itemRepository).findAll();
		verify(itemRepository).saveAll(savedItems.capture());
		verifyNoMoreInteractions(itemRepository);
		assertThat(savedItems.getValue())
				.extracting(InventoryItem::getId, InventoryItem::getStatus)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("A", InventoryItem.STATUS_ACTIVE),
						org.assertj.core.groups.Tuple.tuple("B", InventoryItem.STATUS_ENDED));
	}

	@Test
	void reimportingTheSameFileIsIdempotent() {
		given(itemRepository.findAll()).willReturn(List.of(stored("A", InventoryItem.STATUS_ACTIVE),
				stored("B", InventoryItem.STATUS_ACTIVE)));

		ImportSummary summary = sut.apply(parsed(imported("A"), imported("B")), "listings.csv", NOW, false);

		assertThat(summary).extracting(ImportSummary::added, ImportSummary::updated, ImportSummary::ended)
				.containsExactly(0, 2, 0);
		verify(itemRepository).saveAll(savedItems.capture());
		assertThat(savedItems.getValue()).extracting(InventoryItem::getId).containsExactlyInAnyOrder("A", "B");
	}

	@Test
	void dryRunReportsTheSummaryAndWritesNothing() {
		given(itemRepository.findAll()).willReturn(List.of(stored("B", InventoryItem.STATUS_ACTIVE)));

		ImportSummary summary = sut.apply(parsed(imported("A")), "listings.csv", NOW, true);

		assertThat(summary).extracting(ImportSummary::added, ImportSummary::ended, ImportSummary::dryRun)
				.containsExactly(1, 1, true);
		verify(itemRepository, never()).saveAll(any());
		verifyNoInteractions(metaRepository);
	}

	@Test
	void successfulImportRecordsLastImportAt() {
		given(itemRepository.findAll()).willReturn(List.of());

		sut.apply(parsed(imported("A"), imported("B")), "eBay-active.csv", NOW, false);

		verify(metaRepository).save(savedMeta.capture());
		assertThat(savedMeta.getValue())
				.extracting(InventoryMeta::getId, InventoryMeta::getLastImportAt, InventoryMeta::getItemCount,
						InventoryMeta::getSourceFile)
				.containsExactly(InventoryMeta.SINGLETON_ID, NOW, 2, "eBay-active.csv");
	}

	@Test
	void skippedRowsAreCarriedIntoTheSummary() {
		given(itemRepository.findAll()).willReturn(List.of());
		SkippedRow skipped = new SkippedRow(7, "missing item number");

		ImportSummary summary = sut.apply(new ParsedListings(List.of(imported("A")), List.of(skipped)),
				"listings.csv", NOW, true);

		assertThat(summary.skipped()).containsExactly(skipped);
	}

	private static ParsedListings parsed(InventoryItem... items) {
		return new ParsedListings(List.of(items), List.of());
	}

	private static InventoryItem imported(String id) {
		return new InventoryItem(id, "Card " + id, null, null, new BigDecimal("1.99"), 1, null,
				InventoryItem.STATUS_ACTIVE, NOW);
	}

	private static InventoryItem stored(String id, String status) {
		return new InventoryItem(id, "Card " + id, null, null, new BigDecimal("1.99"), 1, null, status,
				PREVIOUS_IMPORT);
	}

}
