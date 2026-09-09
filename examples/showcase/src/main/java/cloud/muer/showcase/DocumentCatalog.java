package cloud.muer.showcase;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Host-owned document state used by both MVC handlers and IAM resource mapping. */
@Component
public final class DocumentCatalog {
    private final ConcurrentHashMap<String, Document> documents = new ConcurrentHashMap<>();

    public Optional<Document> find(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    public Document update(Document document, String status) {
        var updated = new Document(document.id(), document.projectId(), document.departmentId(), status);
        documents.put(document.id(), updated);
        return updated;
    }

    void seedQuickStartDocuments() {
        documents.clear();
        documents.put("1001", new Document("1001", "101", "10", "DRAFT"));
        documents.put("2001", new Document("2001", "202", "20", "DRAFT"));
    }
}
