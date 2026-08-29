package io.github.iamstarter.persistence.mapper;

import io.github.iamstarter.authorization.PermissionTemplateVersion;
import io.github.iamstarter.persistence.PermissionTemplateVersionRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IamPermissionTemplateVersionMapper {
    PermissionTemplateVersionRow findVersion(@Param("versionId") long versionId);
    List<String> findPermissions(@Param("versionId") long versionId);
    int exists(@Param("versionId") long versionId);
    int insertVersion(@Param("version") PermissionTemplateVersion version);
    int updateVersion(@Param("version") PermissionTemplateVersion version);
    int upsertPermission(@Param("permissionCode") String permissionCode);
    int deletePermissionLinks(@Param("versionId") long versionId);
    int insertPermissionLink(@Param("versionId") long versionId,
                             @Param("permissionCode") String permissionCode);
}
