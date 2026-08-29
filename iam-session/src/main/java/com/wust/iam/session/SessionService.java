package com.wust.iam.session;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SessionService {
    private final SessionRepository sessions;
    private final TokenStore tokens;
    private final Clock clock;

    public SessionService(SessionRepository sessions, TokenStore tokens) {
        this(sessions, tokens, Clock.systemUTC());
    }

    SessionService(SessionRepository sessions, TokenStore tokens, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions);
        this.tokens = Objects.requireNonNull(tokens);
        this.clock = Objects.requireNonNull(clock);
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
    }
}
