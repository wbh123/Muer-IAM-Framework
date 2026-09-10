package cloud.muer.core.model;

import java.util.Objects;

public record ResourceScope(String scopeType, String scopeRefId, ScopeAccess accessMode) {
    public ResourceScope {
        scopeType = requireText(scopeType, "scopeType");
        scopeRefId = requireText(scopeRefId, "scopeRefId");
        accessMode = Objects.requireNonNull(accessMode, "accessMode must not be null");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
