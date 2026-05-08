package com.example.mcp_github.service.rollodocs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class GitHubResponseCache {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private int hits = 0;
    private int misses = 0;

    public void put(String owner, String repo, String path, String type, Object value) {
        cache.put(generateKey(owner, repo, path, type), value);
    }

    public Object get(String owner, String repo, String path, String type) {
        Object value = cache.get(generateKey(owner, repo, path, type));
        if (value != null) {
            hits++;
        } else {
            misses++;
        }
        return value;
    }

    public void clear() {
        cache.clear();
        hits = 0;
        misses = 0;
    }

    public int getHits() {
        return hits;
    }

    public int getMisses() {
        return misses;
    }

    private String generateKey(String owner, String repo, String path, String type) {
        return owner + ":" + repo + ":" + path + ":" + type;
    }
}
