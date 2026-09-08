package cloud.muer.authorization;

import java.util.Set;

/**
 * Transactional command-side port for permission-template lifecycle changes.
 * Generated identifiers and version numbers are repository-owned so callers
 * cannot race or select them themselves.
 */
public interface PermissionTemplateCommandRepository {
    PermissionTemplate createTemplate(String templateKey, String name, String description, boolean enabled);

    PermissionTemplateVersion createNextDraftVersion(long templateId, Set<String> permissions);
}
