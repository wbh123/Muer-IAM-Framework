package io.github.muer.core.model;

import java.util.Objects;

public record IamPrincipal(
        long userId,
        String identityId,
        String identityDomain,
        Long activeProfileId,
        Long templateVersionId,
        String clientType,
        long authorizationVersion) {
    public IamPrincipal {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        identityId = requireText(identityId, "identityId");
        identityDomain = requireText(identityDomain, "identityDomain");
        clientType = requireText(clientType, "clientType");
        if (activeProfileId == null || activeProfileId <= 0) {
            throw new IllegalArgumentException("activeProfileId must be positive");
        }
        if (templateVersionId == null || templateVersionId <= 0) {
            throw new IllegalArgumentException("templateVersionId must be positive");
        }
        if (authorizationVersion < 0) throw new IllegalArgumentException("authorizationVersion must not be negative");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
