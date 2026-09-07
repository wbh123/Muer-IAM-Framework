package io.github.muer.authorization;

public interface PermissionTemplateVersionRepository {
    PermissionTemplateVersion require(long versionId);
    void save(PermissionTemplateVersion version);
}
