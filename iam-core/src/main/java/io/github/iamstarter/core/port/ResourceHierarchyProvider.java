package io.github.iamstarter.core.port;

import io.github.iamstarter.core.model.ResourceDescriptor;
import io.github.iamstarter.core.model.ResourceScope;

public interface ResourceHierarchyProvider {
    boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);
}
