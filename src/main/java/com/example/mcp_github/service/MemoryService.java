package com.example.mcp_github.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import com.example.mcp_github.model.MemoryEntity;
import com.example.mcp_github.repository.MemoryRepository;

/**
 * Service for persistent memory management using MongoDB.
 */
@Service
public class MemoryService {

    private final MemoryRepository memoryRepository;

    public MemoryService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    public void remember(String key, String value) {
        try {
            memoryRepository.save(new MemoryEntity(key, value));
        } catch (Exception e) {
            throw new RuntimeException("Error saving memory to MongoDB: " + e.getMessage());
        }
    }

    public String recall(String key) {
        try {
            return memoryRepository.findById(key)
                    .map(MemoryEntity::getValue)
                    .orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Error reading memory from MongoDB: " + e.getMessage());
        }
    }

    public Map<String, String> recallAll() {
        try {
            return memoryRepository.findAll().stream()
                    .collect(Collectors.toMap(MemoryEntity::getKey, MemoryEntity::getValue));
        } catch (Exception e) {
            throw new RuntimeException("Error reading memory from MongoDB: " + e.getMessage());
        }
    }

    public void forget(String key) {
        try {
            memoryRepository.deleteById(key);
        } catch (Exception e) {
            throw new RuntimeException("Error deleting memory from MongoDB: " + e.getMessage());
        }
    }

    public void forgetAll() {
        try {
            memoryRepository.deleteAll();
        } catch (Exception e) {
            throw new RuntimeException("Error clearing memory in MongoDB: " + e.getMessage());
        }
    }
}
