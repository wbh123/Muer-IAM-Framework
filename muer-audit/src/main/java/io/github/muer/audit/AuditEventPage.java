package io.github.muer.audit;

import java.util.List;
import java.util.Objects;

/**
 * One page of audit events plus the opaque numeric cursor of the last returned
 * event. Events are ordered newest first by their internal log sequence.
 */
public record AuditEventPage(List<AuditEvent> events, long lastLogId) {
    public AuditEventPage {
        events = events == null ? List.of() : List.copyOf(events);
        if (lastLogId < 0) throw new IllegalArgumentException("lastLogId must not be negative");
    }
}
