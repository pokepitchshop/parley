package com.pokepitchshop.parley.inventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.pokepitchshop.parley.inventory.EbayActiveListingsCsvParser.ParsedListings;
import com.pokepitchshop.parley.inventory.EbayActiveListingsCsvParser.SkippedRow;

@Service
public class InventoryImportService {

	private final InventoryItemRepository itemRepository;

	private final InventoryMetaRepository metaRepository;

	public InventoryImportService(InventoryItemRepository itemRepository, InventoryMetaRepository metaRepository) {
		this.itemRepository = itemRepository;
		this.metaRepository = metaRepository;
	}

	/**
	 * Upserts parsed listings by eBay item number and marks previously active listings that are
	 * missing from the file as ended (never deleted). A dry run computes the same summary and
	 * writes nothing; {@code inventory_meta} only moves on a real import.
	 */
	public ImportSummary apply(ParsedListings parsed, String sourceFile, Instant importedAt, boolean dryRun) {
		Map<String, InventoryItem> existing = new HashMap<>();
		for (InventoryItem item : itemRepository.findAll()) {
			existing.put(item.getId(), item);
		}
		Set<String> importedIds = new HashSet<>();
		int added = 0;
		int updated = 0;
		for (InventoryItem item : parsed.items()) {
			importedIds.add(item.getId());
			if (existing.containsKey(item.getId())) {
				updated++;
			}
			else {
				added++;
			}
		}
		List<InventoryItem> ended = existing.values().stream()
				.filter(InventoryItem::isActive)
				.filter(item -> !importedIds.contains(item.getId()))
				.toList();

		if (!dryRun) {
			ended.forEach(item -> item.setStatus(InventoryItem.STATUS_ENDED));
			List<InventoryItem> toSave = new ArrayList<>(parsed.items());
			toSave.addAll(ended);
			itemRepository.saveAll(toSave);
			metaRepository.save(new InventoryMeta(InventoryMeta.SINGLETON_ID, importedAt, parsed.items().size(),
					sourceFile));
		}
		return new ImportSummary(added, updated, ended.size(), parsed.skipped(), dryRun);
	}

	public record ImportSummary(int added, int updated, int ended, List<SkippedRow> skipped, boolean dryRun) {
	}

}
