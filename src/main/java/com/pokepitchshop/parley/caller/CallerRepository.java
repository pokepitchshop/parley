package com.pokepitchshop.parley.caller;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CallerRepository extends MongoRepository<Caller, String> {
}
