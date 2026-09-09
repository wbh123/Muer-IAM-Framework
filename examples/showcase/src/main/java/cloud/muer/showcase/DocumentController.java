package cloud.muer.showcase;

import cloud.muer.autoconfigure.web.RequirePermission;
import cloud.muer.core.model.ScopeAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public final class DocumentController {
    private final DocumentCatalog documents;

    public DocumentController(DocumentCatalog documents) { this.documents = documents; }

    @GetMapping("/public/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("ok"); }

    @GetMapping("/api/documents/{id}")
    @RequirePermission("document:read")
    public ResponseEntity<Document> read(@PathVariable("id") String id) {
        return documents.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/documents/{id}")
    @RequirePermission(value = "document:update", access = ScopeAccess.WRITE)
    public ResponseEntity<Document> update(@PathVariable("id") String id, @RequestBody DocumentUpdate update) {
        return documents.find(id).map(document -> ResponseEntity.ok(documents.update(document, update.status())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
