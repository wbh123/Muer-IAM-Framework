package io.github.muer.core.model;

import java.util.Objects;

public record Identity(String identityId, long userId, String identityKey, String domain, boolean enabled) {
    public Identity {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        identityId = required(identityId, "identityId");
        identityKey = required(identityKey, "identityKey");
        domain = required(domain, "domain");
    }
    public Identity(String identityId, long userId, String domain, boolean enabled) {
        this(identityId, userId, identityId, domain, enabled);
    }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
