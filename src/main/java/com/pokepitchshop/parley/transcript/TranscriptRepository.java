package com.pokepitchshop.parley.transcript;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TranscriptRepository extends MongoRepository<Transcript, String> {
}
