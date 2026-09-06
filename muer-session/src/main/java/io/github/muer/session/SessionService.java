package io.github.muer.session;

import io.github.muer.core.metrics.MuerMetrics;
import io.github.muer.core.metrics.NoOpMuerMetrics;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SessionService {
    private final SessionRepository sessions;
    private final TokenStore tokens;
    private final Clock clock;
    private final MuerMetrics metrics;

    public SessionService(SessionRepository sessions, TokenStore tokens) {
        this(sessions, tokens, Clock.systemUTC(), NoOpMuerMetrics.INSTANCE);
    }

    SessionService(SessionRepository sessions, TokenStore tokens, Clock clock) {
        this(sessions, tokens, clock, NoOpMuerMetrics.INSTANCE);
    }

    public SessionService(SessionRepository sessions, TokenStore tokens, MuerMetrics metrics) {
        this(sessions, tokens, Clock.systemUTC(), metrics);
    }

    SessionService(SessionRepository sessions, TokenStore tokens, Clock clock, MuerMetrics metrics) {
        this.sessions = Objects.requireNonNull(sessions);
        this.tokens = Objects.requireNonNull(tokens);
        this.clock = Objects.requireNonNull(clock);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public void revoke(long userId, String sessionId, String reason) {
        AuthSession session = requireSession(sessionId);
        if (session.userId() != userId) {
            throw new IllegalArgumentException("session does not belong to user");
        }
        revoke(session, reason);
    }

    public void revokeAll(long userId, String reason) {
        sessions.activeForUser(userId).forEach(session -> revoke(session, reason));
    }

    public void forceRevoke(String sessionId, String reason) {
        revoke(requireSession(sessionId), reason);
    }

    public void forceRevokeUser(long userId, String reason) {
        revokeAll(userId, reason);
    }

    public void revokeOthers(long userId, String currentSessionId, String reason) {
        if (currentSessionId == null || currentSessionId.isBlank()) {
            throw new IllegalArgumentException("currentSessionId must not be blank");
        }
        Instant now = clock.instant();
        sessions.activeForUser(userId).stream()
                .filter(session -> !session.sessionId().equals(currentSessionId))
                .forEach(session -> revoke(session, reason, now));
    }

    private AuthSession requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessions.findById(sessionId)
                .filter(session -> !session.revoked())
                .orElseThrow(() -> new IllegalArgumentException("active session not found"));
    }

    private void revoke(AuthSession session, String reason) {
        revoke(session, reason, clock.instant());
    }

    private void revoke(AuthSession session, String reason, Instant occurredAt) {
        sessions.save(session.revoke(occurredAt, reason));
        tokens.revokeSession(session.sessionId());
        recordSessionRevoked();
    }

    private void recordSessionRevoked() {
        try {
            metrics.sessionRevoked();
        } catch (RuntimeException ignored) {
            // Observability must never alter session revocation behavior.
        }
    }
}
