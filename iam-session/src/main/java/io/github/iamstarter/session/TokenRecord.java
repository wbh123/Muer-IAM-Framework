package io.github.iamstarter.session;

import io.github.iamstarter.core.model.IamPrincipal;

import java.time.Instant;
import java.util.Objects;

public record TokenRecord(String sessionId, IamPrincipal principal, Instant expiresAt) {
    public TokenRecord {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId must not be blank");
        principal = Objects.requireNonNull(principal, "principal must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
