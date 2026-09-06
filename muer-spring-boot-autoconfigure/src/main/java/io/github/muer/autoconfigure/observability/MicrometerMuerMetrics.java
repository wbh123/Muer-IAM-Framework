package io.github.muer.autoconfigure.observability;

import io.github.muer.core.metrics.MuerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Micrometer adapter that only emits bounded operational dimensions. */
public final class MicrometerMuerMetrics implements MuerMetrics {
    private static final Set<String> KNOWN_CLIENT_TYPES = Set.of("WEB", "MOBILE", "API", "SERVICE");
    private final MeterRegistry registry;

    public MicrometerMuerMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public void authenticationAttempt(String result, String clientType) {
        registry.counter("muer.authentication.attempts", "result", outcome(result),
                "clientType", clientType(clientType)).increment();
    }

    @Override
    public void authorizationDecision(boolean allowed, String decisionCode, Duration duration) {
        registry.counter("muer.authorization.decisions", "outcome", allowed ? "allow" : "deny").increment();
        Timer.builder("muer.authorization.duration").register(registry)
                .record(Objects.requireNonNull(duration));
    }

    @Override
    public void sessionCreated() {
        registry.counter("muer.sessions.created").increment();
    }

    @Override
    public void sessionRevoked() {
        registry.counter("muer.sessions.revoked").increment();
    }

    @Override
    public void tokenLookup(String result) {
        registry.counter("muer.token.lookups", "result", outcome(result)).increment();
    }

    private static String outcome(String value) {
        return switch (value) {
            case "success", "failure", "hit", "miss", "error" -> value;
            default -> "other";
        };
    }

    private static String clientType(String value) {
        return KNOWN_CLIENT_TYPES.contains(value) ? value : "other";
    }
}
