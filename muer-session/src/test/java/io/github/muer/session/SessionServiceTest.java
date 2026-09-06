package io.github.muer.session;

import io.github.muer.core.metrics.MuerMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceTest {
    @Test
    void revoke_others_keeps_current_session_and_revokes_every_other_active_session() {
        var sessions = new InMemorySessions(List.of(active("current"), active("other-1"), active("other-2")));
        var tokens = new RecordingTokenStore();
        var service = new SessionService(sessions, tokens);

        service.revokeOthers(7L, "current", "USER_REQUEST");

        assertEquals(List.of("current"), sessions.activeForUser(7L).stream().map(AuthSession::sessionId).toList());
        assertEquals(List.of("other-1", "other-2"), tokens.revokedSessions);
    }

    @Test
    void revoke_selected_session_rejects_a_session_owned_by_another_user() {
        var sessions = new InMemorySessions(List.of(active("owned", 7L), active("foreign", 8L)));
        var tokens = new RecordingTokenStore();
        var service = new SessionService(sessions, tokens);

        assertThrows(IllegalArgumentException.class,
                () -> service.revoke(7L, "foreign", "USER_REQUEST"));

        assertEquals(List.of("owned"), sessions.activeForUser(7L).stream().map(AuthSession::sessionId).toList());
        assertEquals(List.of("foreign"), sessions.activeForUser(8L).stream().map(AuthSession::sessionId).toList());
        assertEquals(List.of(), tokens.revokedSessions);
    }

    @Test
    void revoke_all_revokes_every_active_session_for_the_user() {
        var sessions = new InMemorySessions(List.of(active("one"), active("two"), active("foreign", 8L)));
        var tokens = new RecordingTokenStore();
        var service = new SessionService(sessions, tokens);

        service.revokeAll(7L, "USER_REQUEST");

        assertEquals(List.of(), sessions.activeForUser(7L));
        assertEquals(List.of("foreign"), sessions.activeForUser(8L).stream().map(AuthSession::sessionId).toList());
        assertEquals(List.of("one", "two"), tokens.revokedSessions);
    }

    @Test
    void force_revoke_session_does_not_require_the_owners_user_id() {
        var sessions = new InMemorySessions(List.of(active("target", 8L)));
        var tokens = new RecordingTokenStore();
        var service = new SessionService(sessions, tokens);

        service.forceRevoke("target", "ADMIN_ACTION");

        assertEquals(List.of(), sessions.activeForUser(8L));
        var revoked = sessions.findById("target").orElseThrow();
        assertEquals(revoked.revokedAt(), revoked.logoutAt());
        assertEquals(List.of("target"), tokens.revokedSessions);
    }

    @Test
    void force_revoke_user_revokes_all_of_that_users_active_sessions() {
        var sessions = new InMemorySessions(List.of(active("one", 8L), active("two", 8L), active("foreign", 9L)));
        var tokens = new RecordingTokenStore();
        var service = new SessionService(sessions, tokens);

        service.forceRevokeUser(8L, "ADMIN_ACTION");

        assertEquals(List.of(), sessions.activeForUser(8L));
        assertEquals(List.of("foreign"), sessions.activeForUser(9L).stream().map(AuthSession::sessionId).toList());
        assertEquals(List.of("one", "two"), tokens.revokedSessions);
    }

    @Test
    void records_each_successfully_revoked_session() {
        var sessions = new InMemorySessions(List.of(active("one"), active("two")));
        var tokens = new RecordingTokenStore();
        var metrics = new RecordingMetrics();
        var service = new SessionService(sessions, tokens, metrics);

        service.revokeAll(7L, "USER_REQUEST");

        assertEquals(2, metrics.revokedSessions);
    }

    private static AuthSession active(String id) {
        return active(id, 7L);
    }

    private static AuthSession active(String id, long userId) {
        return new AuthSession(id, userId, "WEB", Instant.parse("2026-08-26T00:00:00Z"),
                Instant.parse("2026-08-27T00:00:00Z"), null, null);
    }

    private static final class InMemorySessions implements SessionRepository {
        private final List<AuthSession> values;
        private InMemorySessions(List<AuthSession> values) { this.values = new ArrayList<>(values); }
        public List<AuthSession> activeForUser(long userId) {
            return values.stream().filter(s -> s.userId() == userId && !s.revoked()).toList();
        }
        public Optional<AuthSession> findById(String sessionId) {
            return values.stream().filter(s -> s.sessionId().equals(sessionId)).findFirst();
        }
        public void save(AuthSession session) {
            values.replaceAll(existing -> existing.sessionId().equals(session.sessionId()) ? session : existing);
        }
    }

    private static final class RecordingTokenStore implements TokenStore {
        private final List<String> revokedSessions = new ArrayList<>();
        public void save(String token, TokenRecord record, Duration ttl) { }
        public Optional<TokenRecord> resolve(String token) { return Optional.empty(); }
        public void revoke(String token) { }
        public void revokeSession(String sessionId) { revokedSessions.add(sessionId); }
        public void revokeUser(long userId) { }
        public void refreshTtl(String token, Duration ttl) { }
    }

    private static final class RecordingMetrics implements MuerMetrics {
        private int revokedSessions;

        public void authenticationAttempt(String result, String clientType) { }
        public void authorizationDecision(boolean allowed, String decisionCode, Duration duration) { }
        public void sessionCreated() { }
        public void sessionRevoked() { revokedSessions++; }
        public void tokenLookup(String result) { }
    }
}
