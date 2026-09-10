package cloud.muer.core.port;

import cloud.muer.core.model.OverviewMetrics;

/**
 * Read-only operational overview used by the IAM administration console.
 */
public interface OverviewRepository {
    OverviewMetrics load();
}
