package io.github.iamstarter.example;

import io.github.iamstarter.core.model.ResourceDescriptor;
import io.github.iamstarter.core.model.ResourceScope;
import io.github.iamstarter.core.port.ResourceHierarchyProvider;
import org.springframework.stereotype.Component;

@Component
public final class ExampleResourceHierarchyAdapter implements ResourceHierarchyProvider {
    @Override
    public boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope) {
        if (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId())) {
            return true;
        }
        return resource.parentPath().contains(scope.scopeType() + ":" + scope.scopeRefId());
    }
}
