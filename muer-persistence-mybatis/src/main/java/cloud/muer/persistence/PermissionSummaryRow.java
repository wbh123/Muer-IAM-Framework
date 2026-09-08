package cloud.muer.persistence;

import cloud.muer.authorization.PermissionSummary;

public record PermissionSummaryRow(long permissionId, String permissionCode, String displayName,
                                   String description, boolean enabled, long inUseCount) {
    public PermissionSummary toDomain() {
        return new PermissionSummary(permissionCode, displayName, description, enabled, inUseCount);
    }
}
