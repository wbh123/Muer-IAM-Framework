package com.example.muerquickstart.document;

import cloud.muer.autoconfigure.web.MvcResourceDescriptorResolver;
import cloud.muer.core.model.ResourceDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tells Muer "what is this HTTP request about, and where does that resource
 * live in my hierarchy?" for the declarative MVC authorization path.
 *
 * <p>For {@code /api/documents/{id}} Muer asks this resolver, the resolver
 * loads the document (through {@link DocumentService}) and returns a
 * {@link ResourceDescriptor} whose {@code parentPath} is {@code PROJECT:<id>}.
 * The AuthorizationEngine then checks that path against the caller's Profile
 * scope. Muer never queries your tables; it only consumes what you describe
 * here.</p>
 */
@Component
public class DocumentResourceResolver implements MvcResourceDescriptorResolver {

    private final DocumentService documents;

    public DocumentResourceResolver(DocumentService documents) {
        this.documents = documents;
    }

    @Override
    public Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?> pathVariables)) {
            return Optional.empty();
        }
        Object id = pathVariables.get("id");
        if (!(id instanceof String documentId)) {
            return Optional.empty();
        }
        return documents.find(documentId).map(document -> new ResourceDescriptor(
                "DOCUMENT", document.id(),
                List.of("PROJECT:" + document.projectId()),
                Map.of()));
    }
}
