package com.example.muerquickstart.document;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host-owned in-memory document store. It exists only so the tutorial has
 * concrete documents to protect; replace it with your real repository and
 * nothing else in the Muer integration changes.
 *
 * <p>Seed: {@code 1001 -> Project 101}, {@code 2001 -> Project 202}.</p>
 */
@Service
public class DocumentService {

    private final ConcurrentHashMap<String, Document> documents = new ConcurrentHashMap<>();

    public DocumentService() {
        documents.put("1001", new Document("1001", "101", "DRAFT"));
        documents.put("2001", new Document("2001", "202", "DRAFT"));
    }

    public Optional<Document> find(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    public Document update(String id, String status) {
        var current = documents.get(id);
        if (current == null) {
            throw new IllegalArgumentException("Document not found: " + id);
        }
        var updated = new Document(current.id(), current.projectId(), status);
        documents.put(id, updated);
        return updated;
    }
}
