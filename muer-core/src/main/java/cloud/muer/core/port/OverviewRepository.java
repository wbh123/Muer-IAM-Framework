package io.github.muer.core.port;

import io.github.muer.core.model.OverviewMetrics;

/**
 * Read-only operational overview used by the IAM administration console.
 */
public interface OverviewRepository {
    OverviewMetrics load();
}
