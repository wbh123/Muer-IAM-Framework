package cloud.muer.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, read-only projection of one stored audit event including its
 * subject links. Management consumers must never mutate audit data.
 */
public record AuditEvent(
        String eventId,
        Instant occurredAt,
        String actor,
        String action,
        String resourceType,
        String resourceId,
        String result,
        String requestId,
        Map<String, String> before,
        Map<String, String> after,
        Map<String, String> metadata,
        List<AuditSubjectLink> subjects) {

    public AuditEvent {
        eventId = required(eventId, "eventId");
        actor = required(actor, "actor");
        action = required(action, "action");
        resourceType = required(resourceType, "resourceType");
        resourceId = required(resourceId, "resourceId");
        result = required(result, "result");
        requestId = required(requestId, "requestId");
        before = before == null ? Map.of() : Map.copyOf(before);
        after = after == null ? Map.of() : Map.copyOf(after);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
