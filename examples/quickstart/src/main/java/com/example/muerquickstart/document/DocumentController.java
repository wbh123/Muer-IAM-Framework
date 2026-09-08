package com.example.muerquickstart.document;

import cloud.muer.autoconfigure.web.RequirePermission;
import cloud.muer.core.model.ScopeAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The protected business API of the quick-start.
 *
 * <p>{@code @RequirePermission} tells Muer "only a caller with this permission
 * (and the matching resource scope, resolved through
 * {@code DocumentResourceResolver}) may invoke the handler". Muer enforces it
 * before the method body runs, so the handler never needs to hand-roll
 * authorization.</p>
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    /** Reader can read any document inside its Project scope. */
    @GetMapping("/{id}")
    @RequirePermission("document:read")
    public ResponseEntity<Document> read(@PathVariable("id") String id) {
        return documents.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Updating a document also demands write-level scope inside the project. */
    @PostMapping("/{id}")
    @RequirePermission(value = "document:update", access = ScopeAccess.WRITE)
    public ResponseEntity<Document> update(@PathVariable("id") String id, @RequestBody DocumentUpdate update) {
        return documents.find(id)
                .map(document -> ResponseEntity.ok(documents.update(document.id(), update.status())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
