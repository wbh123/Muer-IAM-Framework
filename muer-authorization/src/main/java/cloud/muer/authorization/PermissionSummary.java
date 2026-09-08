package cloud.muer.authorization;

import java.util.Objects;

/**
 * Read-only projection of one permission registry entry together with the number
 * of template versions that currently reference it.
 */
public record PermissionSummary(String permissionCode, String displayName, String description,
                                boolean enabled, long inUseCount) {

    public PermissionSummary {
        permissionCode = required(permissionCode, "permissionCode");
        displayName = required(displayName, "displayName");
        if (inUseCount < 0) throw new IllegalArgumentException("inUseCount must not be negative");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
