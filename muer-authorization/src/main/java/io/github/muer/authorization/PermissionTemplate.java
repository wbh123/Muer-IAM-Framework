package io.github.muer.authorization;

import java.util.Objects;

/**
 * Metadata of a permission template (the parent of immutable template versions).
 * Introduced as a read model only: administration console queries it, writes go
 * through {@link PermissionTemplateVersionRepository} as before.
 */
public record PermissionTemplate(long templateId, String templateKey, String name,
                                 String description, boolean enabled) {

    public PermissionTemplate {
        if (templateId <= 0) throw new IllegalArgumentException("templateId must be positive");
        templateKey = required(templateKey, "templateKey");
        name = required(name, "name");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
