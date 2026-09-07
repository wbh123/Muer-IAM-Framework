package io.github.muer.core.metrics;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NoOpMuerMetricsTest {
    @Test
    void acceptsAllFrameworkEventsWithoutExternalDependencies() {
        var metrics = NoOpMuerMetrics.INSTANCE;

        assertDoesNotThrow(() -> {
            metrics.authenticationAttempt("success", "WEB");
            metrics.authorizationDecision(true, "ALLOWED", Duration.ofMillis(1));
            metrics.sessionCreated();
            metrics.sessionRevoked();
            metrics.tokenLookup("hit");
        });
    }
}
