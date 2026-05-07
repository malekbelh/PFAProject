package com.example.mcp_github.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.mcp_github.model.MemoryDocument;

/**
 * Repository pour gérer la persistance de la mémoire dans MongoDB.
 */
public interface MemoryRepository extends MongoRepository<MemoryDocument, String> {
}
