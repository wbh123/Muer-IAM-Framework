package cloud.muer.core.port;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;

public interface ResourceHierarchyProvider {
    boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);
}
