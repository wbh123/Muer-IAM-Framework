package cloud.muer.example;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.port.ResourceHierarchyProvider;
import org.springframework.stereotype.Component;

@Component
public final class ExampleResourceHierarchyAdapter implements ResourceHierarchyProvider {
    @Override
    public boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope) {
        // The Starter itself interprets IAM_ADMIN/* for Muer Management resources.
        // This host adapter owns only the example application's business hierarchy.
        if (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId())) {
            return true;
        }
        return resource.parentPath().contains(scope.scopeType() + ":" + scope.scopeRefId());
    }
}
