package io.github.iamstarter.authorization;

import java.util.LinkedHashSet;
import java.util.Objects;

public final class PermissionTemplateService {
    private final PermissionTemplateVersionRepository versions;

    public PermissionTemplateService(PermissionTemplateVersionRepository versions) {
        this.versions = Objects.requireNonNull(versions);
    }

    public void addPermission(long versionId, String permissionCode) {
        var version = versions.require(versionId);
        if (version.status() != TemplateVersionStatus.DRAFT) {
            throw new IllegalStateException("published template versions are immutable");
        }
        var permissions = new LinkedHashSet<>(version.permissions());
        permissions.add(permissionCode);
        versions.save(new PermissionTemplateVersion(version.versionId(), version.templateId(),
                version.versionNumber(), version.status(), permissions));
    }

    public void replace(PermissionTemplateVersion replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        var existing = versions.require(replacement.versionId());
        if (existing.templateId() != replacement.templateId()
                || existing.versionNumber() != replacement.versionNumber()) {
            throw new IllegalArgumentException("template version identity cannot be changed");
        }
        if (existing.status() == TemplateVersionStatus.PUBLISHED && !existing.equals(replacement)) {
            throw new IllegalStateException("published template versions are immutable");
        }
        versions.save(replacement);
    }
}
