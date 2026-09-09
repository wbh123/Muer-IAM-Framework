package cloud.muer.persistence.mapper;

import cloud.muer.authorization.PermissionTemplateVersion;
import cloud.muer.persistence.PermissionTemplateVersionRow;
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
    int insertTemplate(@Param("templateKey") String templateKey, @Param("name") String name,
                       @Param("description") String description, @Param("enabled") boolean enabled);
    int nextVersionNumberForUpdate(@Param("templateId") long templateId);
    int insertDraftVersion(@Param("templateId") long templateId, @Param("versionNumber") int versionNumber);
    long lastInsertId();
}
