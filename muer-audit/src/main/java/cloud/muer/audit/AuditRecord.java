package cloud.muer.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AuditRecord(String auditId, String operator, String action, String resourceType,
                          String resourceId, String result, String requestId,
                          Map<String, String> before, Map<String, String> after,
                          Map<String, String> metadata, Instant occurredAt) {
    public AuditRecord {
        auditId = required(auditId, "auditId");
        operator = required(operator, "operator");
        action = required(action, "action");
        resourceType = required(resourceType, "resourceType");
        resourceId = required(resourceId, "resourceId");
        result = required(result, "result");
        requestId = required(requestId, "requestId");
        before = before == null ? Map.of() : Map.copyOf(before);
        after = after == null ? Map.of() : Map.copyOf(after);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
