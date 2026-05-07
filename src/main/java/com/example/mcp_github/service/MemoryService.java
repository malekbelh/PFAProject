package com.example.mcp_github.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.mcp_github.model.MemoryDocument;
import com.example.mcp_github.repository.MemoryRepository;

/**
 * Service de gestion de la mémoire persistante via MongoDB. Remplace l'ancien
 * stockage local JSON.
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryRepository repository;

    public MemoryService(MemoryRepository repository) {
        this.repository = repository;
    }

    public synchronized void remember(String key, String value) {
        log.debug("Memory: Retaining {} = {}", key, value);
        repository.save(new MemoryDocument(key, value));
    }

    public synchronized String recall(String key) {
        return repository.findById(key)
                .map(MemoryDocument::getValue)
                .orElse(null);
    }

    public synchronized Map<String, String> recallAll() {
        return repository.findAll().stream()
                .filter(doc -> doc.getId() != null && doc.getValue() != null)
                .collect(Collectors.toMap(
                        MemoryDocument::getId,
                        MemoryDocument::getValue,
                        (v1, v2) -> v1
                ));
    }

    public synchronized void forget(String key) {
        log.debug("Memory: Forgetting {}", key);
        repository.deleteById(key);
    }

    public synchronized void forgetAll() {
        log.warn("Memory: Clearing entire project memory in MongoDB");
        repository.deleteAll();
    }
}
