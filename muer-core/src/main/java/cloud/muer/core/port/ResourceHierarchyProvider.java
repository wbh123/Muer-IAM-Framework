package io.github.muer.core.port;

import io.github.muer.core.model.ResourceDescriptor;
import io.github.muer.core.model.ResourceScope;

public interface ResourceHierarchyProvider {
    boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);
}
