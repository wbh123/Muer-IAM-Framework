package cloud.muer.http;

import cloud.muer.authentication.AuthenticationService;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.session.TokenRecord;
import cloud.muer.session.TokenStore;
import cloud.muer.session.AuthSession;
import cloud.muer.session.SessionRepository;
import cloud.muer.session.SessionService;
import cloud.muer.session.LoginEvent;
import cloud.muer.session.LoginEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamAuthenticationControllerTest {
    @AfterEach
    void clear_security_context() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successful_login_returns_issued_opaque_token_and_principal() throws Exception {
        var tokens = new InMemoryTokens();
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 31L, 9L, "WEB", 4L);
        var authentication = new AuthenticationService(tokens, ignored -> 4L,
                request -> request.username().equals("operator") && request.password().equals("secret")
                        ? Optional.of(principal) : Optional.empty(),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC), Duration.ofHours(8),
                () -> "opaque-token", () -> "session-7");
        var mvc = MockMvcBuilders.standaloneSetup(controller(authentication, tokens, new InMemorySessions())).build();

        mvc.perform(post("/iam/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"secret","clientType":"WEB"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("opaque-token"))
                .andExpect(jsonPath("$.sessionId").value("session-7"))
                .andExpect(jsonPath("$.principal.userId").value(7))
                .andExpect(jsonPath("$.principal.activeProfileId").value(31));
    }

    @Test
    void invalid_credentials_return_unauthorized_without_issuing_a_token() throws Exception {
        var tokens = new InMemoryTokens();
        var authentication = authentication(tokens);
        var mvc = MockMvcBuilders.standaloneSetup(controller(authentication, tokens, new InMemorySessions())).build();

        mvc.perform(post("/iam/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"wrong","clientType":"WEB"}
                                """))
                .andExpect(status().isUnauthorized());

        assertFalse(tokens.contains("opaque-token"));
    }

    @Test
    void login_uses_server_remote_address_instead_of_untrusted_forwarded_header() throws Exception {
        var tokens = new InMemoryTokens();
        var sessions = new InMemorySessions();
        var events = new CapturingLoginEvents();
        var authentication = new AuthenticationService(tokens, sessions, events, ignored -> 4L,
                request -> Optional.of(principal()),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC), Duration.ofHours(8),
                Duration.ofMinutes(10), () -> "event-7", () -> "opaque-token", () -> "session-7");
        var mvc = MockMvcBuilders.standaloneSetup(controller(authentication, tokens, sessions)).build();

        mvc.perform(post("/iam/auth/login")
                        .with(request -> { request.setRemoteAddr("203.0.113.7"); return request; })
                        .header("X-Forwarded-For", "198.51.100.9")
                        .header("User-Agent", "test-browser/1.0")
                        .header("X-Request-ID", "request-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"secret","clientType":"WEB"}
                                """))
                .andExpect(status().isOk());

        assertEquals("203.0.113.7", events.saved.ipAddress());
        assertEquals("test-browser/1.0", events.saved.userAgent());
        assertEquals("request-7", events.saved.requestId());
    }

    @Test
    void login_sanitizes_and_bounds_client_controlled_audit_headers() throws Exception {
        var tokens = new InMemoryTokens();
        var sessions = new InMemorySessions();
        var events = new CapturingLoginEvents();
        var authentication = new AuthenticationService(tokens, sessions, events, ignored -> 4L,
                request -> Optional.of(principal()),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC), Duration.ofHours(8),
                Duration.ofMinutes(10), () -> "event-8", () -> "opaque-token", () -> "session-8");
        var mvc = MockMvcBuilders.standaloneSetup(controller(authentication, tokens, sessions)).build();

        mvc.perform(post("/iam/auth/login")
                        .header("User-Agent", "browser\t" + "x".repeat(1100))
                        .header("X-Request-ID", "r".repeat(200))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"secret","clientType":"WEB"}
                                """))
                .andExpect(status().isOk());

        assertEquals(1024, events.saved.userAgent().length());
        assertFalse(events.saved.userAgent().contains("\t"));
        assertEquals(128, events.saved.requestId().length());
    }

    @Test
    void current_principal_comes_from_the_security_context() throws Exception {
        var tokens = new InMemoryTokens();
        var principal = principal();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(
                controller(authentication(tokens), tokens, new InMemorySessions())).build();

        mvc.perform(get("/iam/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.identityId").value("identity-7"))
                .andExpect(jsonPath("$.authorizationVersion").value(4));
    }

    @Test
    void logout_revokes_the_durable_session_and_every_token_bound_to_it() throws Exception {
        var tokens = new InMemoryTokens();
        var record = new TokenRecord("session-7", principal(), Instant.parse("2026-08-27T00:00:00Z"));
        tokens.save("opaque-token", record, Duration.ofHours(8));
        tokens.save("other-token", record, Duration.ofHours(8));
        var sessions = new InMemorySessions(new AuthSession("session-7", 7L, "WEB",
                Instant.parse("2026-08-26T00:00:00Z"), Instant.parse("2026-08-27T00:00:00Z"), null, null));
        var mvc = MockMvcBuilders.standaloneSetup(
                controller(authentication(tokens), tokens, sessions)).build();

        mvc.perform(post("/iam/auth/logout").header("Authorization", "Bearer opaque-token"))
                .andExpect(status().isNoContent());

        assertFalse(tokens.contains("opaque-token"));
        assertFalse(tokens.contains("other-token"));
        org.junit.jupiter.api.Assertions.assertTrue(sessions.findById("session-7").orElseThrow().revoked());
    }

    @Test
    void current_principal_requires_an_iam_security_context() throws Exception {
        var tokens = new InMemoryTokens();
        var mvc = MockMvcBuilders.standaloneSetup(
                controller(authentication(tokens), tokens, new InMemorySessions())).build();

        mvc.perform(get("/iam/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_requires_a_bearer_token() throws Exception {
        var tokens = new InMemoryTokens();
        var mvc = MockMvcBuilders.standaloneSetup(
                controller(authentication(tokens), tokens, new InMemorySessions())).build();

        mvc.perform(post("/iam/auth/logout").header("Authorization", "Basic credentials"))
                .andExpect(status().isUnauthorized());
    }

    private static AuthenticationService authentication(InMemoryTokens tokens) {
        return new AuthenticationService(tokens, ignored -> 4L,
                request -> request.username().equals("operator") && request.password().equals("secret")
                        ? Optional.of(principal()) : Optional.empty(),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC), Duration.ofHours(8),
                () -> "opaque-token", () -> "session-7");
    }

    private static IamPrincipal principal() {
        return new IamPrincipal(7L, "identity-7", "SECURITY", 31L, 9L, "WEB", 4L);
    }

    private static IamAuthenticationController controller(AuthenticationService authentication,
                                                          InMemoryTokens tokens,
                                                          InMemorySessions sessions) {
        return new IamAuthenticationController(authentication, tokens, new SessionService(sessions, tokens));
    }

    private static final class InMemorySessions implements SessionRepository {
        private final List<AuthSession> values = new ArrayList<>();
        private InMemorySessions(AuthSession... initial) { values.addAll(List.of(initial)); }
        public List<AuthSession> activeForUser(long userId) {
            return values.stream().filter(value -> value.userId() == userId && !value.revoked()).toList();
        }
        public Optional<AuthSession> findById(String sessionId) {
            return values.stream().filter(value -> value.sessionId().equals(sessionId)).findFirst();
        }
        public void save(AuthSession session) {
            values.removeIf(value -> value.sessionId().equals(session.sessionId()));
            values.add(session);
        }
    }

    private static final class InMemoryTokens implements TokenStore {
        private final Map<String, TokenRecord> records = new HashMap<>();

        public void save(String token, TokenRecord record, Duration ttl) { records.put(token, record); }
        public Optional<TokenRecord> resolve(String token) { return Optional.ofNullable(records.get(token)); }
        public void revoke(String token) { records.remove(token); }
        public void revokeSession(String sessionId) { records.entrySet().removeIf(e -> e.getValue().sessionId().equals(sessionId)); }
        public void revokeUser(long userId) { records.entrySet().removeIf(e -> e.getValue().principal().userId() == userId); }
        public void refreshTtl(String token, Duration ttl) { }
        boolean contains(String token) { return records.containsKey(token); }
    }

    private static final class CapturingLoginEvents implements LoginEventRepository {
        private LoginEvent saved;
        public void append(LoginEvent event) { saved = event; }
    }
}
