package cloud.muer.autoconfigure.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerMuerMetricsTest {
    @Test
    void records_low_cardinality_operational_metrics() {
        var registry = new SimpleMeterRegistry();
        var metrics = new MicrometerMuerMetrics(registry);

        metrics.authenticationAttempt("success", "WEB");
        metrics.authorizationDecision(false, "PERMISSION_DENIED", Duration.ofMillis(12));
        metrics.sessionCreated();
        metrics.sessionRevoked();
        metrics.tokenLookup("hit");

        assertEquals(1.0, registry.get("muer.authentication.attempts").tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("muer.authorization.decisions").tag("outcome", "deny").counter().count());
        assertEquals(1L, registry.get("muer.authorization.duration").timer().count());
        assertEquals(1.0, registry.get("muer.sessions.created").counter().count());
        assertEquals(1.0, registry.get("muer.sessions.revoked").counter().count());
        assertEquals(1.0, registry.get("muer.token.lookups").tag("result", "hit").counter().count());
    }
}
