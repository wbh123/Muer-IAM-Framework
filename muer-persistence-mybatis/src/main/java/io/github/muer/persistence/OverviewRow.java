package io.github.muer.persistence;

import io.github.muer.core.model.OverviewMetrics;

public record OverviewRow(long totalUsers, long enabledUsers, long activeSessions,
                          long activeProfiles, long templateVersions, long auditEventsToday) {
    public OverviewMetrics toDomain() {
        return new OverviewMetrics(totalUsers, enabledUsers, activeSessions, activeProfiles,
                templateVersions, auditEventsToday);
    }
}
