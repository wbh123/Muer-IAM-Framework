package io.github.iamstarter.authorization;

import java.util.Set;

public record PermissionTemplateVersion(long versionId, long templateId, int versionNumber,
                                        TemplateVersionStatus status, Set<String> permissions) {
    public PermissionTemplateVersion {
        if (versionId <= 0 || templateId <= 0 || versionNumber <= 0) {
            throw new IllegalArgumentException("template version identifiers must be positive");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
