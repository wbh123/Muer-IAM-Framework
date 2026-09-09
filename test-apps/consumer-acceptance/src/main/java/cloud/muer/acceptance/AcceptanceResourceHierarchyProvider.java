package cloud.muer.acceptance;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.port.ResourceHierarchyProvider;
import org.springframework.stereotype.Component;

@Component
public class AcceptanceResourceHierarchyProvider implements ResourceHierarchyProvider {
    @Override
    public boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope) {
        return (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId()))
                || resource.parentPath().contains(scope.scopeType() + ":" + scope.scopeRefId());
    }
}
