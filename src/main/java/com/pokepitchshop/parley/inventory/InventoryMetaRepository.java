package com.pokepitchshop.parley.inventory;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryMetaRepository extends MongoRepository<InventoryMeta, String> {
}
