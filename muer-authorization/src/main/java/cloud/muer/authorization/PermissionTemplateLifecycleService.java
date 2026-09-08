package cloud.muer.authorization;

import java.util.Objects;
import java.util.Set;

/** Enforces the create, draft and publish state transitions for templates. */
public final class PermissionTemplateLifecycleService {
    private final PermissionTemplateCommandRepository commands;
    private final PermissionTemplateVersionRepository versions;

    public PermissionTemplateLifecycleService(PermissionTemplateCommandRepository commands,
                                              PermissionTemplateVersionRepository versions) {
        this.commands = Objects.requireNonNull(commands, "commands must not be null");
        this.versions = Objects.requireNonNull(versions, "versions must not be null");
    }

    public PermissionTemplate createTemplate(String templateKey, String name, String description) {
        return commands.createTemplate(templateKey, name, description, true);
    }

    public PermissionTemplateVersion createDraftVersion(long templateId, Set<String> permissions) {
        return commands.createNextDraftVersion(templateId, permissions == null ? Set.of() : Set.copyOf(permissions));
    }

    public PermissionTemplateVersion publish(long versionId) {
        var draft = versions.require(versionId);
        if (draft.status() != TemplateVersionStatus.DRAFT) {
            throw new IllegalStateException("only draft template versions can be published");
        }
        var published = new PermissionTemplateVersion(draft.versionId(), draft.templateId(), draft.versionNumber(),
                TemplateVersionStatus.PUBLISHED, draft.permissions());
        versions.save(published);
        return published;
    }
}
