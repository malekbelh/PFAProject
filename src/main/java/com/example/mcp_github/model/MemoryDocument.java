package com.example.mcp_github.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entité représentant une donnée en mémoire persistée dans MongoDB.
 */
@Document(collection = "project_memory")
public class MemoryDocument {

    @Id
    private String id; // Sera la clé (ex: "current_owner")
    private String value;

    public MemoryDocument() {
    }

    public MemoryDocument(String id, String value) {
        this.id = id;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
