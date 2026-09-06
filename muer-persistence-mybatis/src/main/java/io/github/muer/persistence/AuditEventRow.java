package io.github.muer.persistence;

import java.time.Instant;

/**
 * Raw audit log projection. JSON payload columns are carried as text and decoded
 * by the query repository; subject links are attached separately per page.
 */
public record AuditEventRow(long logId, String eventId, Instant occurredAt, String actor,
                            String action, String resourceType, String resourceId, String result,
                            String requestId, String beforeJson, String afterJson,
                            String metadataJson) {
}
