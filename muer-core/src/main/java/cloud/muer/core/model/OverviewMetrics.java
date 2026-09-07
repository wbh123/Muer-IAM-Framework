package cloud.muer.core.model;

/**
 * Lightweight operational counters shown on the administration dashboard.
 *
 * <p>All values are read-only aggregates; the console never mutates them.
 */
public record OverviewMetrics(
        long totalUsers,
        long enabledUsers,
        long activeSessions,
        long activeProfiles,
        long templateVersions,
        long auditEventsToday) {

    public OverviewMetrics {
        if (totalUsers < 0 || enabledUsers < 0 || activeSessions < 0 || activeProfiles < 0
                || templateVersions < 0 || auditEventsToday < 0) {
            throw new IllegalArgumentException("overview counters must not be negative");
        }
    }
}
