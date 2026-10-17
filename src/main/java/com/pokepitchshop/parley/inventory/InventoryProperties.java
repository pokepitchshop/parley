package com.pokepitchshop.parley.inventory;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param refreshInterval how often to check {@code inventory_meta.lastImportAt} for a new import
 * @param maxMatches      most items returned per utterance
 * @param minScore        minimum overlapping meaningful tokens for an item to match
 * @param synonymGroups   card-speak synonyms, one group per entry, terms separated by {@code |};
 *                        the first term is canonical (e.g. {@code upper deck|ud})
 */
@ConfigurationProperties(prefix = "parley.inventory")
public record InventoryProperties(
		@DefaultValue("PT5M") Duration refreshInterval,
		@DefaultValue("3") int maxMatches,
		@DefaultValue("1") int minScore,
		@DefaultValue List<String> synonymGroups) {

	public InventoryProperties {
		synonymGroups = synonymGroups == null ? List.of() : List.copyOf(synonymGroups);
	}

}
