package io.github.muer.audit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only audit query surface used by the IAM administration console.
 *
 * <p>This SPI is intentionally separate from {@link AuditRepository} so that
 * existing consumer audit implementations keep working without change. The
 * returned events are immutable and must never be updated or deleted.
 */
public interface AuditQueryRepository {

    Optional<AuditEvent> findById(String eventId);

    /**
     * Returns up to {@code limit} events whose internal log id is strictly less
     * than {@code maxLogId} (0 means "from the newest event"), newest first.
     */
    AuditEventPage findEvents(AuditEventFilter filter, long maxLogId, int limit);

    default AuditEventPage findEvents(AuditEventFilter filter) {
        return findEvents(Objects.requireNonNull(filter, "filter must not be null"), 0L, 100);
    }
}
