package io.github.iamstarter.core.port;

import io.github.iamstarter.core.model.OverviewMetrics;

/**
 * Read-only operational overview used by the IAM administration console.
 */
public interface OverviewRepository {
    OverviewMetrics load();
}
