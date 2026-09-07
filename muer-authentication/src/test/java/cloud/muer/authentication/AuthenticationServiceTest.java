package cloud.muer.authentication;

import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.metrics.MuerMetrics;
import cloud.muer.session.TokenRecord;
import cloud.muer.session.TokenStore;
import cloud.muer.session.AuthSession;
import cloud.muer.session.SessionRepository;
import cloud.muer.session.LoginEvent;
import cloud.muer.session.LoginEventRepository;
import cloud.muer.session.LoginResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationServiceTest {
    @Test
    void token_resolution_rejects_stale_authorization_version() {
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var store = new SingleTokenStore(new TokenRecord("session-1", principal, Instant.parse("2026-08-27T00:00:00Z")));
        var service = new AuthenticationService(store, userId -> 3L);

        assertTrue(service.resolve("opaque").isEmpty());
    }

    @Test
    void successful_login_issues_one_token_bound_to_one_session() {
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var store = new SingleTokenStore(null);
        var clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);
        var service = new AuthenticationService(store, userId -> 2L,
                request -> Optional.of(principal), clock, Duration.ofHours(8),
                () -> "token-1", () -> "session-1");

        var result = service.login(new LoginRequest("alex", "secret", "WEB")).orElseThrow();

        assertEquals("token-1", result.accessToken());
        assertEquals("session-1", result.sessionId());
        assertEquals("session-1", store.record.sessionId());
    }

    @Test
    void records_authentication_and_token_lookup_outcomes_without_sensitive_values() {
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var metrics = new RecordingMetrics();
        var service = new AuthenticationService(new SingleTokenStore(new TokenRecord(
                "session-1", principal, Instant.parse("2026-08-27T00:00:00Z"))), new CapturingSessions(),
                event -> { }, userId -> 2L, request -> Optional.of(principal),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC), Duration.ofHours(8),
                Duration.ofMinutes(10), () -> "event-1", () -> "token-1", () -> "session-1", metrics);

        service.login(new LoginRequest("alex", "secret", "WEB")).orElseThrow();
        service.resolve("opaque");
        service.resolve("unknown");

        assertEquals(List.of("success:WEB"), metrics.authentication);
        assertEquals(List.of("hit", "miss"), metrics.tokenLookups);
    }

    @Test
    void successful_login_persists_the_management_session_projection() {
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var tokens = new SingleTokenStore(null);
        var sessions = new CapturingSessions();
        var clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);
        var service = new AuthenticationService(tokens, sessions, userId -> 2L,
                request -> Optional.of(principal), clock, Duration.ofHours(8),
                () -> "token-1", () -> "session-1");

        service.login(new LoginRequest("alex", "secret", "WEB")).orElseThrow();

        assertEquals("session-1", sessions.saved.sessionId());
        assertEquals(7L, sessions.saved.userId());
        assertEquals(Instant.parse("2026-08-26T00:00:00Z"), sessions.saved.loginAt());
        assertEquals(Instant.parse("2026-08-26T08:00:00Z"), sessions.saved.expiresAt());
    }

    @Test
    void failed_session_persistence_compensates_by_revoking_the_issued_token() {
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var tokens = new SingleTokenStore(null);
        SessionRepository sessions = new SessionRepository() {
            public List<AuthSession> activeForUser(long userId) { return List.of(); }
            public Optional<AuthSession> findById(String sessionId) { return Optional.empty(); }
            public void save(AuthSession session) { throw new IllegalStateException("database unavailable"); }
        };
        var service = new AuthenticationService(tokens, sessions, userId -> 2L,
                request -> Optional.of(principal), Clock.systemUTC(), Duration.ofHours(8),
                () -> "token-1", () -> "session-1");

        assertThrows(IllegalStateException.class,
                () -> service.login(new LoginRequest("alex", "secret", "WEB")));
        assertTrue(tokens.revoked);
        assertTrue(tokens.record == null);
    }

    @Test
    void resolving_a_token_touches_the_session_only_after_acquiring_the_distributed_lease() {
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var tokens = new SingleTokenStore(new TokenRecord("session-1", principal,
                Instant.parse("2026-08-26T08:00:00Z")));
        tokens.touchLeaseGranted = true;
        var sessions = new CapturingSessions();
        var now = Instant.parse("2026-08-26T01:00:00Z");
        var service = new AuthenticationService(tokens, sessions, userId -> 2L,
                request -> Optional.empty(), Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(8),
                Duration.ofMinutes(10), () -> "token-1", () -> "session-1");

        assertTrue(service.resolve("opaque").isPresent());

        assertEquals("session-1", tokens.touchLeaseSessionId);
        assertEquals(Duration.ofMinutes(10), tokens.touchLeaseInterval);
        assertEquals("session-1", sessions.touchedSessionId);
        assertEquals(now, sessions.touchedAt);
    }

    @Test
    void resolving_a_token_skips_database_touch_while_the_lease_is_held() {
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var tokens = new SingleTokenStore(new TokenRecord("session-1", principal,
                Instant.parse("2026-08-26T08:00:00Z")));
        var sessions = new CapturingSessions();
        var service = new AuthenticationService(tokens, sessions, userId -> 2L,
                request -> Optional.empty(), Clock.fixed(Instant.parse("2026-08-26T01:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(8), Duration.ofMinutes(10), () -> "token-1", () -> "session-1");

        assertTrue(service.resolve("opaque").isPresent());

        assertEquals(null, sessions.touchedSessionId);
    }

    @Test
    void session_touch_failure_does_not_reject_an_otherwise_valid_token() {
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 2L);
        var tokens = new SingleTokenStore(new TokenRecord("session-1", principal,
                Instant.parse("2026-08-26T08:00:00Z")));
        tokens.touchLeaseGranted = true;
        SessionRepository sessions = new SessionRepository() {
            public List<AuthSession> activeForUser(long userId) { return List.of(); }
            public Optional<AuthSession> findById(String sessionId) { return Optional.empty(); }
            public void save(AuthSession session) { }
            public void touch(String sessionId, Instant lastSeenAt) {
                throw new IllegalStateException("database unavailable");
            }
        };
        var service = new AuthenticationService(tokens, sessions, userId -> 2L,
                request -> Optional.empty(), Clock.fixed(Instant.parse("2026-08-26T01:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(8), Duration.ofMinutes(10), () -> "token-1", () -> "session-1");

        assertEquals(principal, service.resolve("opaque").orElseThrow());
    }

    @Test
    void successful_login_appends_a_success_event_after_session_creation() {
        var principal = new IamPrincipal(7L, "id-7", "ACCOUNT", 19L, 3L, "WEB", 2L);
        var events = new CapturingLoginEvents();
        var sessions = new CapturingSessions();
        var now = Instant.parse("2026-08-26T01:00:00Z");
        var service = new AuthenticationService(new SingleTokenStore(null), sessions, events,
                userId -> 2L, request -> Optional.of(principal), Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofHours(8), Duration.ofMinutes(10), () -> "event-1",
                () -> "token-1", () -> "session-1");

        service.login(new LoginRequest("alex", "secret", "WEB", "browser-1")).orElseThrow();

        assertEquals("event-1", events.saved.eventId());
        assertEquals(7L, events.saved.userId());
        assertEquals("alex", events.saved.identityKey());
        assertEquals("ACCOUNT", events.saved.identityDomain());
        assertEquals("session-1", events.saved.sessionId());
        assertEquals("browser-1", events.saved.clientInstance());
        assertEquals("browser-1", sessions.saved.clientInstance());
        assertEquals(LoginResult.SUCCESS, events.saved.result());
        assertEquals(now, events.saved.occurredAt());
    }

    @Test
    void successful_login_copies_request_metadata_to_session_and_security_event() {
        var principal = new IamPrincipal(7L, "id-7", "ACCOUNT", 19L, 3L, "WEB", 2L);
        var events = new CapturingLoginEvents();
        var sessions = new CapturingSessions();
        var service = new AuthenticationService(new SingleTokenStore(null), sessions, events,
                userId -> 2L, request -> Optional.of(principal),
                Clock.fixed(Instant.parse("2026-08-26T01:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(8), Duration.ofMinutes(10), () -> "event-metadata",
                () -> "token-metadata", () -> "session-metadata");

        service.login(new LoginRequest(
                "alex", "secret", "WEB", "browser-1",
                "203.0.113.7", "Mozilla/5.0", "DESKTOP", "Linux", "Firefox", "1.2.0", "request-7"))
                .orElseThrow();

        assertEquals("203.0.113.7", sessions.saved.ipAddress());
        assertEquals("Mozilla/5.0", sessions.saved.userAgent());
        assertEquals("203.0.113.7", events.saved.ipAddress());
        assertEquals("Mozilla/5.0", events.saved.userAgent());
        assertEquals("DESKTOP", events.saved.deviceType());
        assertEquals("Linux", events.saved.osName());
        assertEquals("Firefox", events.saved.browserName());
        assertEquals("1.2.0", events.saved.appVersion());
        assertEquals("request-7", events.saved.requestId());
    }

    @Test
    void rejected_credentials_append_a_failed_login_event_without_secret_material() {
        var events = new CapturingLoginEvents();
        var service = new AuthenticationService(new SingleTokenStore(null), new CapturingSessions(), events,
                userId -> 2L, request -> Optional.empty(),
                Clock.fixed(Instant.parse("2026-08-26T01:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(8), Duration.ofMinutes(10), () -> "event-2",
                () -> "token-1", () -> "session-1");

        assertTrue(service.login(new LoginRequest("alex", "do-not-record", "WEB")).isEmpty());

        assertEquals(null, events.saved.userId());
        assertEquals("alex", events.saved.identityKey());
        assertEquals(LoginResult.FAILED, events.saved.result());
        assertEquals("CREDENTIAL_REJECTED", events.saved.reasonCode());
        assertTrue(!events.saved.toString().contains("do-not-record"));
    }

    @Test
    void authenticator_failure_is_audited_and_the_original_failure_is_preserved() {
        var events = new CapturingLoginEvents();
        var failure = new IllegalStateException("identity provider unavailable");
        var service = new AuthenticationService(new SingleTokenStore(null), new CapturingSessions(), events,
                userId -> 2L, request -> { throw failure; },
                Clock.fixed(Instant.parse("2026-08-26T01:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(8), Duration.ofMinutes(10), () -> "event-3",
                () -> "token-1", () -> "session-1");

        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> service.login(new LoginRequest("alex", "secret", "WEB"))));
        assertEquals(LoginResult.FAILED, events.saved.result());
        assertEquals("AUTHENTICATOR_ERROR", events.saved.reasonCode());
    }

    @Test
    void login_event_failure_revokes_the_issued_token_and_durable_session() {
        var principal = new IamPrincipal(7L, "id-7", "ACCOUNT", 19L, 3L, "WEB", 2L);
        var tokens = new SingleTokenStore(null);
        var sessions = new CapturingSessions();
        var auditFailure = new IllegalStateException("audit database unavailable");
        LoginEventRepository events = event -> { throw auditFailure; };
        var service = new AuthenticationService(tokens, sessions, events, userId -> 2L,
                request -> Optional.of(principal),
                Clock.fixed(Instant.parse("2026-08-26T01:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(8), Duration.ofMinutes(10), () -> "event-4",
                () -> "token-4", () -> "session-4");

        assertEquals(auditFailure, assertThrows(IllegalStateException.class,
                () -> service.login(new LoginRequest("alex", "secret", "WEB"))));

        assertTrue(tokens.revoked);
        assertTrue(sessions.saved.revoked());
        assertEquals("LOGIN_AUDIT_FAILED", sessions.saved.revokeReason());
    }

    private static final class SingleTokenStore implements TokenStore {
        private TokenRecord record;
        private boolean revoked;
        private boolean touchLeaseGranted;
        private String touchLeaseSessionId;
        private Duration touchLeaseInterval;
        private SingleTokenStore(TokenRecord record) { this.record = record; }
        public Optional<TokenRecord> resolve(String token) { return "opaque".equals(token) ? Optional.ofNullable(record) : Optional.empty(); }
        public void save(String token, TokenRecord record, Duration ttl) { this.record = record; }
        public void revoke(String token) { revoked = true; record = null; }
        public void revokeSession(String sessionId) { }
        public void revokeUser(long userId) { }
        public void refreshTtl(String token, Duration ttl) { }
        public boolean acquireSessionTouchLease(String sessionId, Duration interval) {
            touchLeaseSessionId = sessionId;
            touchLeaseInterval = interval;
            return touchLeaseGranted;
        }
    }

    private static final class RecordingMetrics implements MuerMetrics {
        private final List<String> authentication = new java.util.ArrayList<>();
        private final List<String> tokenLookups = new java.util.ArrayList<>();

        @Override public void authenticationAttempt(String result, String clientType) {
            authentication.add(result + ":" + clientType);
        }
        @Override public void authorizationDecision(boolean allowed, String decisionCode, Duration duration) { }
        @Override public void sessionCreated() { }
        @Override public void sessionRevoked() { }
        @Override public void tokenLookup(String result) { tokenLookups.add(result); }
    }

    private static final class CapturingSessions implements SessionRepository {
        private AuthSession saved;
        private String touchedSessionId;
        private Instant touchedAt;
        public List<AuthSession> activeForUser(long userId) { return saved == null ? List.of() : List.of(saved); }
        public Optional<AuthSession> findById(String sessionId) { return Optional.ofNullable(saved); }
        public void save(AuthSession session) { saved = session; }
        public void touch(String sessionId, Instant lastSeenAt) {
            touchedSessionId = sessionId;
            touchedAt = lastSeenAt;
        }
    }

    private static final class CapturingLoginEvents implements LoginEventRepository {
        private LoginEvent saved;
        public void append(LoginEvent event) { saved = event; }
    }
}
