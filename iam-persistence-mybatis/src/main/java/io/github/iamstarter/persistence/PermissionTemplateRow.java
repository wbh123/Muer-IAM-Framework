package io.github.iamstarter.persistence;

import io.github.iamstarter.authorization.PermissionTemplate;

public record PermissionTemplateRow(long templateId, String templateKey, String name,
                                    String description, boolean enabled) {
    public PermissionTemplate toDomain() {
        return new PermissionTemplate(templateId, templateKey, name, description, enabled);
    }
}
