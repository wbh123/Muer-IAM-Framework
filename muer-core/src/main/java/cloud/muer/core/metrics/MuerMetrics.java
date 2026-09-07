package io.github.muer.core.metrics;

import java.time.Duration;

/** Framework-neutral runtime instrumentation boundary. */
public interface MuerMetrics {
    void authenticationAttempt(String result, String clientType);

    void authorizationDecision(boolean allowed, String decisionCode, Duration duration);

    void sessionCreated();

    void sessionRevoked();

    void tokenLookup(String result);
}
