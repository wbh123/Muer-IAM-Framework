package cloud.muer.autoconfigure;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;

import java.util.Set;

/**
 * Interprets Muer's own administrative root scope without claiming host resources.
 */
final class MuerManagementResourceHierarchyProvider {
    private static final Set<String> MANAGEMENT_RESOURCE_TYPES = Set.of(
            "IAM_AUDIT_COLLECTION",
            "IAM_AUDIT_LOG",
            "IAM_AUTHORIZATION_PROFILE",
            "IAM_AUTHORIZATION_PROFILE_COLLECTION",
            "IAM_IDENTITY",
            "IAM_OVERVIEW",
            "IAM_PERMISSION_COLLECTION",
            "IAM_PERMISSION_TEMPLATE",
            "IAM_PERMISSION_TEMPLATE_COLLECTION",
            "IAM_PERMISSION_TEMPLATE_VERSION",
            "IAM_SESSION",
            "IAM_SESSION_COLLECTION",
            "IAM_USER",
            "IAM_USER_COLLECTION");

    public boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope) {
        return "IAM_ADMIN".equals(scope.scopeType())
                && "*".equals(scope.scopeRefId())
                && MANAGEMENT_RESOURCE_TYPES.contains(resource.resourceType());
    }
}
