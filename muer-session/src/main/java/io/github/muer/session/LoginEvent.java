package io.github.muer.session;

import java.time.Instant;
import java.util.Objects;

public record LoginEvent(String eventId, Long userId, String identityKey, String identityDomain,
                         String clientType, String clientInstance, String ipAddress, String userAgent,
                         String deviceType, String osName, String browserName, String appVersion, String sessionId,
                         LoginResult result, String reasonCode,
                         String requestId, Instant occurredAt) {
    public LoginEvent {
        requireText(eventId, "eventId");
        if (userId != null && userId <= 0) throw new IllegalArgumentException("userId must be positive");
        requireText(identityKey, "identityKey");
        requireText(clientType, "clientType");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (result == LoginResult.FAILED) requireText(reasonCode, "reasonCode");
    }

    public LoginEvent(String eventId, Long userId, String identityKey, String identityDomain,
                      String clientType, String clientInstance, String sessionId,
                      LoginResult result, String reasonCode, String requestId, Instant occurredAt) {
        this(eventId, userId, identityKey, identityDomain, clientType, clientInstance,
                null, null, null, null, null, null, sessionId, result, reasonCode, requestId, occurredAt);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
