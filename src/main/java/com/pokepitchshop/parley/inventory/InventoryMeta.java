package com.pokepitchshop.parley.inventory;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single document describing the last successful inventory import. {@link InventoryIndex}
 * reloads only when {@code lastImportAt} changes.
 */
@Document(collection = "inventory_meta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMeta {

	public static final String SINGLETON_ID = "inventory";

	@Id
	private String id;

	private Instant lastImportAt;

	private int itemCount;

	private String sourceFile;

}
