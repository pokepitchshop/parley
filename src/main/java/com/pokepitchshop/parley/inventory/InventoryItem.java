package com.pokepitchshop.parley.inventory;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One eBay listing, imported from the Seller Hub Active Listings export (POK-480).
 */
@Document(collection = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

	public static final String STATUS_ACTIVE = "active";

	public static final String STATUS_ENDED = "ended";

	/** eBay item number. */
	@Id
	private String id;

	private String title;

	private String sku;

	private String category;

	private BigDecimal price;

	private int quantity;

	private String condition;

	private String status;

	private Instant importedAt;

	public boolean isActive() {
		return STATUS_ACTIVE.equals(status);
	}

}
