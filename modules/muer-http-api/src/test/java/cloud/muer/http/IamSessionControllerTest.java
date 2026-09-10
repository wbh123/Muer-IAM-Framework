package cloud.muer.http;

import cloud.muer.core.model.IamPrincipal;
import cloud.muer.session.AuthSession;
import cloud.muer.session.SessionRepository;
import cloud.muer.session.SessionService;
import cloud.muer.session.TokenRecord;
import cloud.muer.session.TokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamSessionControllerTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forged_user_query_cannot_replace_authenticated_principal() throws Exception {
        var sessions = new InMemorySessions(List.of(
                session("session-7", 7L),
                session("session-999", 999L)));
        var controller = new IamSessionController(sessions, new SessionService(sessions, new NoOpTokenStore()));
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 31L, 9L, "WEB", 4L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/iam/sessions").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(7))
                .andExpect(jsonPath("$.items[0].sessionId").value("session-7"))
                .andExpect(jsonPath("$.items[0].lastSeenAt").value("2026-08-26T00:00:00.000Z"));
    }

    private static AuthSession session(String sessionId, long userId) {
        return new AuthSession(sessionId, userId, "WEB", Instant.parse("2026-08-26T00:00:00Z"),
                Instant.parse("2026-08-27T00:00:00Z"), null, null);
    }

    private static final class InMemorySessions implements SessionRepository {
        private final List<AuthSession> sessions;

        private InMemorySessions(List<AuthSession> sessions) {
            this.sessions = new ArrayList<>(sessions);
        }

        @Override
        public List<AuthSession> activeForUser(long userId) {
            return sessions.stream().filter(session -> session.userId() == userId && !session.revoked()).toList();
        }

        @Override
        public Optional<AuthSession> findById(String sessionId) {
            return sessions.stream().filter(session -> session.sessionId().equals(sessionId)).findFirst();
        }

        @Override
        public void save(AuthSession session) {
            sessions.removeIf(existing -> existing.sessionId().equals(session.sessionId()));
            sessions.add(session);
        }
    }

    private static final class NoOpTokenStore implements TokenStore {
        public void save(String token, TokenRecord record, Duration ttl) { }
        public Optional<TokenRecord> resolve(String token) { return Optional.empty(); }
        public void revoke(String token) { }
        public void revokeSession(String sessionId) { }
        public void revokeUser(long userId) { }
        public void refreshTtl(String token, Duration ttl) { }
    }
}
