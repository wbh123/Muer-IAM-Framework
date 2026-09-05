package io.github.iamstarter.audit;

import java.time.Instant;

/**
 * Optional filters for audit event search. A {@code null} field disables that
 * predicate. Subject-based filters match the subject link written together with
 * the event: {@code userId} matches subject type {@code USER},
 * {@code identityId} matches subject type {@code IDENTITY}, and {@code sessionId}
 * matches subject type {@code SESSION}.
 */
public record AuditEventFilter(
        Long userId,
        String identityId,
        String sessionId,
        String eventType,
        String resourceType,
        String resourceId,
        Instant from,
        Instant to) {
}
