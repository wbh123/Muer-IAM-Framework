package io.github.iamstarter.example;

import io.github.iamstarter.autoconfigure.web.MvcResourceDescriptorResolver;
import io.github.iamstarter.core.model.ResourceDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maps this host application's document route to an IAM resource descriptor. */
@Component
public final class ExampleDocumentResourceResolver implements MvcResourceDescriptorResolver {
    private final DocumentCatalog documents;

    public ExampleDocumentResourceResolver(DocumentCatalog documents) {
        this.documents = documents;
    }

    @Override
    public Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?> pathVariables)) return Optional.empty();
        Object id = pathVariables.get("id");
        if (!(id instanceof String documentId)) return Optional.empty();
        return documents.find(documentId).map(document -> new ResourceDescriptor(
                "DOCUMENT", document.id(),
                List.of("PROJECT:" + document.projectId(), "DEPARTMENT:" + document.departmentId()), Map.of()));
    }
}
