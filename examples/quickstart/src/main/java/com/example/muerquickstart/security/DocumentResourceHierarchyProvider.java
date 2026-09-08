package com.example.muerquickstart.security;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.port.ResourceHierarchyProvider;
import org.springframework.stereotype.Component;

/**
 * Decides whether a business resource is inside a given {@link ResourceScope}.
 *
 * <p>The quick-start hierarchy is flat: a {@code PROJECT:<id>} scope is "inside"
 * when the resource is that project (the resolved descriptor's parent path
 * contains {@code PROJECT:<id>}) or is the project itself. Document 1001 lives
 * under {@code PROJECT:101}, so scope {@code PROJECT/101/READ} matches it;
 * Document 2001 under {@code PROJECT:202} does not.</p>
 */
@Component
public class DocumentResourceHierarchyProvider implements ResourceHierarchyProvider {

    @Override
    public boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope) {
        if (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId())) {
            return true;
        }
        return resource.parentPath().contains(scope.scopeType() + ":" + scope.scopeRefId());
    }
}
