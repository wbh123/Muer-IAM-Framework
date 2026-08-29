package com.wust.iam.session;

import java.time.Instant;
import java.util.Objects;

public record AuthSession(String sessionId, long userId, String clientType, String clientInstance,
                          String ipAddress, String userAgent, Instant loginAt, Instant lastSeenAt,
                          Instant expiresAt, Instant revokedAt, Instant logoutAt, String revokeReason) {
    public AuthSession {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId must not be blank");
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (clientType == null || clientType.isBlank()) throw new IllegalArgumentException("clientType must not be blank");
        Objects.requireNonNull(loginAt, "loginAt must not be null");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public AuthSession(String sessionId, long userId, String clientType, Instant loginAt,
                       Instant expiresAt, Instant revokedAt, String revokeReason) {
        this(sessionId, userId, clientType, null, null, null, loginAt, loginAt, expiresAt,
                revokedAt, revokedAt, revokeReason);
    }

    public AuthSession(String sessionId, long userId, String clientType, String clientInstance,
                       Instant loginAt, Instant lastSeenAt, Instant expiresAt,
                       Instant revokedAt, String revokeReason) {
        this(sessionId, userId, clientType, clientInstance, null, null, loginAt, lastSeenAt, expiresAt,
                revokedAt, revokedAt, revokeReason);
    }

    public boolean revoked() { return revokedAt != null; }

    public AuthSession revoke(Instant occurredAt, String reason) {
        var logoutTime = Objects.requireNonNull(occurredAt);
        return new AuthSession(sessionId, userId, clientType, clientInstance, ipAddress, userAgent,
                loginAt, lastSeenAt, expiresAt, logoutTime, logoutTime, reason);
    }

    public AuthSession touch(Instant occurredAt) {
        return new AuthSession(sessionId, userId, clientType, clientInstance, ipAddress, userAgent, loginAt,
                Objects.requireNonNull(occurredAt), expiresAt, revokedAt, logoutAt, revokeReason);
    }
}
