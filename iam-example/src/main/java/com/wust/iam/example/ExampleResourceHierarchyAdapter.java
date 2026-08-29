package com.wust.iam.example;

import com.wust.iam.core.model.ResourceDescriptor;
import com.wust.iam.core.model.ResourceScope;
import com.wust.iam.core.port.ResourceHierarchyProvider;
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
