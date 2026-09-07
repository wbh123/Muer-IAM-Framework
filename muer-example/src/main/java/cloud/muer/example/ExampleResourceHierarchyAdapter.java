package cloud.muer.example;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.port.ResourceHierarchyProvider;
import org.springframework.stereotype.Component;

@Component
public final class ExampleResourceHierarchyAdapter implements ResourceHierarchyProvider {
    @Override
    public boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope) {
        // IAM management resources are IAM_* descriptors. A scope of type IAM_ADMIN
        // with reference '*' grants the management console scope for this example's
        // own IAM_* resources. It never grants host business resources (PROJECT etc.),
        // which continue to require their concrete type/id scopes below.
        if ("IAM_ADMIN".equals(scope.scopeType()) && "*".equals(scope.scopeRefId())
                && resource.resourceType().startsWith("IAM_")) {
            return true;
        }
        if (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId())) {
            return true;
        }
        return resource.parentPath().contains(scope.scopeType() + ":" + scope.scopeRefId());
    }
}
