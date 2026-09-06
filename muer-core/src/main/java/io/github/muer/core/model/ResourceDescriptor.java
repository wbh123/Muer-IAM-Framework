package io.github.iamstarter.core.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ResourceDescriptor(String resourceType, String resourceId, List<String> parentPath, Map<String, String> attributes) {
    public ResourceDescriptor {
        resourceType = required(resourceType, "resourceType");
        resourceId = required(resourceId, "resourceId");
        parentPath = parentPath == null ? List.of() : List.copyOf(parentPath);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
