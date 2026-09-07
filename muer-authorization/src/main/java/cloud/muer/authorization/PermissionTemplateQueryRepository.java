package io.github.muer.authorization;

import java.util.List;
import java.util.Optional;

/**
 * Read-side permission template and registry queries used by the IAM
 * administration console.
 *
 * <p>All methods are read-only; they never mutate template versions or the
 * permission registry.
 */
public interface PermissionTemplateQueryRepository {

    Optional<PermissionTemplate> findTemplate(long templateId);

    /**
     * Cursor-paginated template metadata, ordered by ascending template id strictly
     * after {@code afterTemplateId}.
     */
    List<PermissionTemplate> listTemplates(long afterTemplateId, int limit);

    /**
     * All versions of a template ordered by version number descending. A template
     * without versions yields an empty list.
     */
    List<PermissionTemplateVersion> findVersionsByTemplate(long templateId);

    Optional<PermissionTemplateVersion> findVersionById(long versionId);

    /**
     * Cursor-paginated permission registry entries ordered by ascending registry id.
     *
     * @param keyword case-insensitive substring filter on code or display name
     * @param domain optional filter: permission codes whose leading segment
     *        (before the first {@code .}) equals this value; {@code null} disables it
     */
    PermissionSummaryPage listPermissions(String keyword, String domain,
                                          long afterPermissionId, int limit);
}
