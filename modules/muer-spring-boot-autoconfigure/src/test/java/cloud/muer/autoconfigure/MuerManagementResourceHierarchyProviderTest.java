package cloud.muer.autoconfigure;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.ScopeAccess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuerManagementResourceHierarchyProviderTest {
    private final MuerManagementResourceHierarchyProvider hierarchy =
            new MuerManagementResourceHierarchyProvider();

    @Test
    void management_root_scope_covers_every_real_management_resource_type() {
        var scope = new ResourceScope("IAM_ADMIN", "*", ScopeAccess.READ);

        List.of(
                "IAM_AUDIT_COLLECTION", "IAM_AUDIT_LOG", "IAM_AUTHORIZATION_PROFILE",
                "IAM_AUTHORIZATION_PROFILE_COLLECTION", "IAM_IDENTITY", "IAM_OVERVIEW",
                "IAM_PERMISSION_COLLECTION", "IAM_PERMISSION_TEMPLATE",
                "IAM_PERMISSION_TEMPLATE_COLLECTION", "IAM_PERMISSION_TEMPLATE_VERSION",
                "IAM_SESSION", "IAM_SESSION_COLLECTION", "IAM_USER", "IAM_USER_COLLECTION")
                .forEach(type -> assertTrue(hierarchy.isWithinScope(resource(type), scope), type));
    }

    @Test
    void management_root_scope_does_not_cover_host_or_lookalike_resources() {
        var scope = new ResourceScope("IAM_ADMIN", "*", ScopeAccess.READ);

        assertFalse(hierarchy.isWithinScope(resource("DOCUMENT"), scope));
        assertFalse(hierarchy.isWithinScope(resource("IAM_DEVICE"), scope));
        assertFalse(hierarchy.isWithinScope(resource("IAM_ADMIN"), scope));
    }

    @Test
    void only_the_management_root_scope_is_recognized() {
        assertFalse(hierarchy.isWithinScope(resource("IAM_USER"),
                new ResourceScope("IAM_ADMIN", "admin-7", ScopeAccess.READ)));
        assertFalse(hierarchy.isWithinScope(resource("IAM_USER"),
                new ResourceScope("PROJECT", "*", ScopeAccess.READ)));
    }

    private static ResourceDescriptor resource(String type) {
        return new ResourceDescriptor(type, "resource-1", List.of(), Map.of());
    }
}
