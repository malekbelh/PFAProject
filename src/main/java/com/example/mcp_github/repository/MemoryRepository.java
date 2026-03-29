package com.example.mcp_github.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.mcp_github.model.MemoryEntity;

/**
 * Repository interface for MemoryEntity.
 */
public interface MemoryRepository extends MongoRepository<MemoryEntity, String> {
}
