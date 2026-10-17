package com.pokepitchshop.parley.inventory;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryItemRepository extends MongoRepository<InventoryItem, String> {

	List<InventoryItem> findByStatus(String status);

}
