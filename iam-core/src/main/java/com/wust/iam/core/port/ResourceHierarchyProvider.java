package com.wust.iam.core.port;

import com.wust.iam.core.model.ResourceDescriptor;
import com.wust.iam.core.model.ResourceScope;

public interface ResourceHierarchyProvider {
    boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);
}
