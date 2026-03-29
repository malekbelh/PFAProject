package com.example.mcp_github.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "memory")
public class MemoryEntity {

    @Id
    private String id;

    private String value;

    public MemoryEntity() {
    }

    public MemoryEntity(String key, String value) {
        this.id = key;
        this.value = value;
    }

    public String getKey() {
        return id;
    }

    public String getValue() {
        return value;
    }

    public void setKey(String key) {
        this.id = key;
    }

    public void setValue(String value) {
        this.value = value;
    }
}