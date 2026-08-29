package com.wust.iam.core.model;

import java.util.Objects;
import java.util.Set;

/**
 * Optional permission grouping metadata. Runtime authorization still evaluates atomic permissions.
 */
public record Role(String roleKey, String displayName, String domain,
                   Set<String> permissionCodes, boolean enabled) {
    public Role {
        roleKey = requireText(roleKey, "roleKey");
        displayName = requireText(displayName, "displayName");
        domain = requireText(domain, "domain");
        permissionCodes = Set.copyOf(Objects.requireNonNull(permissionCodes,
                "permissionCodes must not be null"));
        if (permissionCodes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("permissionCodes must not contain blank values");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
