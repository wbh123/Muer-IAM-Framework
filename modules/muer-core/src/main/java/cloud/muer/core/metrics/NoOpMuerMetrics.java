package cloud.muer.core.metrics;

import java.time.Duration;

/** Default instrumentation implementation for applications without a metrics backend. */
public enum NoOpMuerMetrics implements MuerMetrics {
    INSTANCE;

    @Override public void authenticationAttempt(String result, String clientType) { }
    @Override public void authorizationDecision(boolean allowed, String decisionCode, Duration duration) { }
    @Override public void sessionCreated() { }
    @Override public void sessionRevoked() { }
    @Override public void tokenLookup(String result) { }
}
