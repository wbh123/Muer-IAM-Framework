package io.github.iamstarter.authorization;

public interface PermissionTemplateVersionRepository {
    PermissionTemplateVersion require(long versionId);
    void save(PermissionTemplateVersion version);
}
