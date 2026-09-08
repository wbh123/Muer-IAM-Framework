package cloud.muer.persistence;

import cloud.muer.authorization.PermissionTemplate;

public record PermissionTemplateRow(long templateId, String templateKey, String name,
                                    String description, boolean enabled) {
    public PermissionTemplate toDomain() {
        return new PermissionTemplate(templateId, templateKey, name, description, enabled);
    }
}
