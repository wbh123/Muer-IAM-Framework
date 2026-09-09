package cloud.muer.authorization;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionTemplateLifecycleServiceTest {
    @Test
    void creates_template_and_server_numbered_draft_version() {
        var commands = new Commands();
        var service = new PermissionTemplateLifecycleService(commands, commands);

        var template = service.createTemplate("billing", "Billing", "Billing access");
        var version = service.createDraftVersion(template.templateId(), Set.of("billing.read"));

        assertEquals(1L, template.templateId());
        assertEquals(1, version.versionNumber());
        assertEquals(TemplateVersionStatus.DRAFT, version.status());
    }

    @Test
    void publishes_only_drafts_and_published_versions_are_immutable() {
        var commands = new Commands();
        var draft = commands.createNextDraftVersion(1L, Set.of("billing.read"));
        var service = new PermissionTemplateLifecycleService(commands, commands);

        assertEquals(TemplateVersionStatus.PUBLISHED, service.publish(draft.versionId()).status());
        assertThrows(IllegalStateException.class, () -> service.publish(draft.versionId()));
    }

    private static final class Commands implements PermissionTemplateCommandRepository, PermissionTemplateVersionRepository {
        private long templateId;
        private long versionId;
        private final Map<Long, PermissionTemplateVersion> versions = new HashMap<>();

        public PermissionTemplate createTemplate(String key, String name, String description, boolean enabled) {
            return new PermissionTemplate(++templateId, key, name, description, enabled);
        }

        public PermissionTemplateVersion createNextDraftVersion(long templateId, Set<String> permissions) {
            int number = (int) versions.values().stream().filter(v -> v.templateId() == templateId).count() + 1;
            var version = new PermissionTemplateVersion(++versionId, templateId, number, TemplateVersionStatus.DRAFT, permissions);
            versions.put(version.versionId(), version);
            return version;
        }

        public PermissionTemplateVersion require(long id) { return versions.get(id); }
        public void save(PermissionTemplateVersion version) { versions.put(version.versionId(), version); }
    }
}
