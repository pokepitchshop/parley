package com.pokepitchshop.parley.shop;

import java.time.Instant;

/**
 * Catalog card mirrored from IOM {@code cards} — populated in Phase 1 (POK-446).
 */
public record CatalogCard(
		String id,
		String game,
		String name,
		String setCode,
		String collectorNumber,
		String variant,
		Instant updatedAt) {
}
