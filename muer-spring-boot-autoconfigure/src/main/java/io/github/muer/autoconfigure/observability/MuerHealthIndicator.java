package io.github.muer.autoconfigure.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** Reports framework availability only; datastore connectivity belongs to the host application. */
public final class MuerHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        return Health.up().withDetail("muer", "available").build();
    }
}
