package io.github.iamstarter.example;

import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationRequest;
import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.core.model.ResourceDescriptor;
import io.github.iamstarter.core.model.ScopeAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public final class DocumentController {
    private final AuthorizationEngine authorization;
    private final Map<String, Document> documents = Map.of("1001", new Document("1001", "101", "10", "DRAFT"));

    public DocumentController(AuthorizationEngine authorization) { this.authorization = authorization; }

    @GetMapping("/public/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("ok"); }

    @GetMapping("/api/documents/{id}")
    public ResponseEntity<Document> read(@PathVariable String id) {
        var principal = principal();
        if (principal == null) return ResponseEntity.status(401).build();
        var document = documents.get(id);
        if (document == null) return ResponseEntity.notFound().build();
        var resource = new ResourceDescriptor("DOCUMENT", document.id(),
                List.of("PROJECT:" + document.projectId(), "DEPARTMENT:" + document.departmentId()), Map.of());
        var request = new AuthorizationRequest("document:read", principal.identityDomain(), principal.clientType(), resource, ScopeAccess.READ);
        return authorization.decide(principal, request).allowed() ? ResponseEntity.ok(document) : ResponseEntity.status(403).build();
    }

    private static IamPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof IamPrincipal principal ? principal : null;
    }
}
