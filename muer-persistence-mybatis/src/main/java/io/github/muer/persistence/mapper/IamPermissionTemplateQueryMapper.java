package io.github.muer.persistence.mapper;

import io.github.muer.authorization.PermissionTemplateVersion;
import io.github.muer.authorization.TemplateVersionStatus;
import io.github.muer.persistence.PermissionSummaryRow;
import io.github.muer.persistence.PermissionTemplateRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Read-side mapper for permission templates, their versions and the permission
 * registry. All statements are read-only.
 */
public interface IamPermissionTemplateQueryMapper {

    PermissionTemplateRow findTemplate(@Param("templateId") long templateId);

    List<PermissionTemplateRow> listTemplates(@Param("afterTemplateId") long afterTemplateId,
                                              @Param("limit") int limit);

    List<VersionProjection> listVersions(@Param("templateId") long templateId);

    List<VersionProjection> findVersion(@Param("versionId") long versionId);

    List<String> findPermissions(@Param("versionId") long versionId);

    List<PermissionSummaryRow> listPermissions(@Param("afterPermissionId") long afterPermissionId,
                                               @Param("limit") int limit,
                                               @Param("keyword") String keyword,
                                               @Param("domain") String domain);

    /**
     * Lightweight version projection used by the query mapper.
     */
    record VersionProjection(long versionId, long templateId, int versionNumber,
                             TemplateVersionStatus status) {
    }
}
