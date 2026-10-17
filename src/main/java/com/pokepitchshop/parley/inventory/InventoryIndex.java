package com.pokepitchshop.parley.inventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory copy of active eBay listings for per-turn stock lookups (POK-481). {@link #match}
 * never touches MongoDB; {@link #refreshIfChanged} swaps in a new snapshot only when a new
 * import has landed, and keeps the previous snapshot if MongoDB is unreachable.
 */
@Slf4j
@Component
public class InventoryIndex {

	private final InventoryItemRepository itemRepository;

	private final InventoryMetaRepository metaRepository;

	private final InventoryProperties properties;

	private final InventoryTextNormalizer normalizer;

	private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

	private final AtomicBoolean refreshFailureLogged = new AtomicBoolean();

	public InventoryIndex(
			InventoryItemRepository itemRepository,
			InventoryMetaRepository metaRepository,
			InventoryProperties properties) {
		this.itemRepository = itemRepository;
		this.metaRepository = metaRepository;
		this.properties = properties;
		this.normalizer = new InventoryTextNormalizer(properties.synonymGroups());
	}

	@Scheduled(fixedDelayString = "${parley.inventory.refresh-interval:PT5M}")
	public void refreshIfChanged() {
		try {
			Instant lastImportAt = metaRepository.findById(InventoryMeta.SINGLETON_ID)
					.map(InventoryMeta::getLastImportAt)
					.orElse(null);
			if (lastImportAt == null || lastImportAt.equals(snapshot.get().lastImportAt())) {
				return;
			}
			List<InventoryItem> active = itemRepository.findByStatus(InventoryItem.STATUS_ACTIVE);
			snapshot.set(buildSnapshot(active, lastImportAt));
			refreshFailureLogged.set(false);
			log.info("Inventory index loaded items={} lastImportAt={}", snapshot.get().itemCount(), lastImportAt);
		}
		catch (RuntimeException ex) {
			if (refreshFailureLogged.compareAndSet(false, true)) {
				log.warn("Inventory refresh failed; keeping previous snapshot items={}",
						snapshot.get().itemCount(), ex);
			}
		}
	}

	/** Top matching active listings for a caller utterance; empty when nothing shopping-related matches. */
	public List<InventoryItem> match(String utterance) {
		Snapshot current = snapshot.get();
		Set<String> wanted = normalizer.tokens(utterance);
		if (wanted.isEmpty() || current.itemCount() == 0) {
			return List.of();
		}
		Map<IndexedItem, Integer> scores = new HashMap<>();
		for (String token : wanted) {
			for (IndexedItem candidate : current.byToken().getOrDefault(token, List.of())) {
				scores.merge(candidate, 1, Integer::sum);
			}
		}
		return scores.entrySet().stream()
				.filter(entry -> entry.getValue() >= properties.minScore())
				.sorted(Comparator.<Map.Entry<IndexedItem, Integer>>comparingInt(Map.Entry::getValue).reversed()
						.thenComparing(entry -> entry.getKey().item().getQuantity(), Comparator.reverseOrder())
						.thenComparing(entry -> entry.getKey().item().getImportedAt(),
								Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(properties.maxMatches())
				.map(entry -> entry.getKey().item())
				.toList();
	}

	private Snapshot buildSnapshot(List<InventoryItem> items, Instant lastImportAt) {
		Map<String, List<IndexedItem>> byToken = new HashMap<>();
		int count = 0;
		for (InventoryItem item : items) {
			if (!item.isActive()) {
				continue;
			}
			IndexedItem indexed = new IndexedItem(item);
			for (String token : normalizer.tokens(item.getTitle())) {
				byToken.computeIfAbsent(token, key -> new ArrayList<>()).add(indexed);
			}
			count++;
		}
		byToken.replaceAll((token, list) -> List.copyOf(list));
		return new Snapshot(Map.copyOf(byToken), count, lastImportAt);
	}

	/** Identity wrapper so equal-looking listings never collapse into one score bucket. */
	private record IndexedItem(InventoryItem item) {

		@Override
		public boolean equals(Object other) {
			return other instanceof IndexedItem indexed && indexed.item == item;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(item);
		}
	}

	private record Snapshot(Map<String, List<IndexedItem>> byToken, int itemCount, Instant lastImportAt) {

		static final Snapshot EMPTY = new Snapshot(Map.of(), 0, null);

		Snapshot {
			Objects.requireNonNull(byToken);
		}
	}

}
