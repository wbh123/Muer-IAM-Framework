package io.github.iamstarter.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IamPrincipalTest {
    @Test
    void principal_carries_exactly_one_active_profile_and_template_version() {
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 31L, 9L, "WEB", 4L);

        assertEquals(31L, principal.activeProfileId());
        assertEquals(9L, principal.templateVersionId());
    }

    @Test
    void scope_rejects_blank_type() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceScope("", "42", ScopeAccess.READ));
    }

    @Test
    void resource_descriptor_copies_attributes_and_preserves_parent_path() {
        var descriptor = new ResourceDescriptor("ORDER", "o-7", List.of("ORG:1", "DEPARTMENT:7"), Map.of("status", "PENDING"));

        assertEquals("ORDER", descriptor.resourceType());
        assertEquals(List.of("ORG:1", "DEPARTMENT:7"), descriptor.parentPath());
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.attributes().put("status", "APPROVED"));
    }

    @Test
    void role_is_permission_grouping_metadata_with_an_immutable_permission_set() {
        var role = new Role("FINANCE", "Finance", "ACCOUNT", Set.of("order.read", "order.refund"), true);

        assertEquals(Set.of("order.read", "order.refund"), role.permissionCodes());
        assertThrows(UnsupportedOperationException.class, () -> role.permissionCodes().add("order.approve"));
    }
}
