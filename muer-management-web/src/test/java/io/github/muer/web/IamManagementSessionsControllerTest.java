package io.github.muer.web;

import io.github.muer.authorization.AuthorizationDecision;
import io.github.muer.authorization.AuthorizationEngine;
import io.github.muer.core.model.IamPrincipal;
import io.github.muer.core.model.IamUser;
import io.github.muer.core.port.UserQueryRepository;
import io.github.muer.session.AuthSession;
import io.github.muer.session.SessionQueryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamManagementSessionsControllerTest {
    private static final Instant NOW = Instant.now();
    private static final AuthSession ACTIVE = new AuthSession("session-a", 7L, "WEB", "browser-1",
            NOW.minusSeconds(3600), NOW.minusSeconds(60), NOW.plusSeconds(3600), null, null);
    private static final AuthSession REVOKED = new AuthSession("session-b", 7L, "WEB", "browser-2",
            NOW.minusSeconds(7200), NOW.minusSeconds(1200), NOW.plusSeconds(3600),
            NOW.minusSeconds(600), "ADMIN_ACTION");

    @AfterEach
    void clear_security_context() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void session_listing_returns_status_without_token_material() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(get("/iam/admin/sessions").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[1].status").value("REVOKED"))
                .andExpect(jsonPath("$.items[1].revokeReason").value("ADMIN_ACTION"))
                .andExpect(jsonPath("$.items[0].userId").value(7));
    }

    @Test
    void session_pagination_uses_the_opaque_cursor() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        var first = mvc.perform(get("/iam/admin/sessions").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sessionId").value("session-a"))
                .andExpect(jsonPath("$.nextAfter").isNotEmpty())
                .andReturn();

        // The cursor is opaque; requesting the next page continues after the first item.
        var firstJson = first.getResponse().getContentAsString();
        var cursor = firstJson.replaceFirst(".*\\\"nextAfter\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mvc.perform(get("/iam/admin/sessions").param("after", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sessionId").value("session-b"))
                .andExpect(jsonPath("$.nextAfter").isEmpty());
    }

    @Test
    void session_queries_require_the_session_read_permission() throws Exception {
        authenticate();
        var fixture = fixture(false);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/sessions")).andExpect(status().isForbidden());
        mvc.perform(get("/iam/admin/sessions/session-a")).andExpect(status().isForbidden());
    }

    @Test
    void session_detail_and_user_sessions_are_returned_to_authorized_readers() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/sessions/session-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-a"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/iam/admin/users/7/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unknown_session_or_user_returns_not_found() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/sessions/nope")).andExpect(status().isNotFound());
        mvc.perform(get("/iam/admin/users/999/sessions")).andExpect(status().isNotFound());
    }

    private static void authenticate() {
        var principal = new IamPrincipal(99L, "administrator", "SECURITY", 40L, 10L, "WEB", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static Fixture fixture(boolean allowed) {
        AuthorizationEngine engine = (principal, request) ->
                new AuthorizationDecision(allowed, allowed ? "ALLOW" : "DENY", List.of());
        return new Fixture(new IamManagementSessionsController(engine, new FakeSessions(), new FakeUsers()));
    }

    private record Fixture(IamManagementSessionsController controller) {
    }

    private static final class FakeSessions implements SessionQueryRepository {
        private final List<AuthSession> values = new ArrayList<>(List.of(ACTIVE, REVOKED));

        @Override
        public Optional<AuthSession> findById(String sessionId) {
            return values.stream().filter(value -> value.sessionId().equals(sessionId)).findFirst();
        }

        @Override
        public List<AuthSession> findByUserId(long userId) {
            return values.stream().filter(value -> value.userId() == userId).toList();
        }

        @Override
        public List<AuthSession> search(Instant afterLoginAt, String afterSessionId, int limit,
                                        Long userId, String clientType, Boolean active) {
            var stream = values.stream();
            if (afterSessionId != null) {
                var cursorLogin = afterLoginAt;
                stream = stream.filter(value -> value.loginAt().isBefore(cursorLogin)
                        || (value.loginAt().equals(cursorLogin)
                        && value.sessionId().compareTo(afterSessionId) < 0));
            }
            return stream.sorted((left, right) -> {
                int byLogin = right.loginAt().compareTo(left.loginAt());
                return byLogin != 0 ? byLogin : right.sessionId().compareTo(left.sessionId());
            }).limit(limit).toList();
        }
    }

    private static final class FakeUsers implements UserQueryRepository {
        private final List<IamUser> values = new ArrayList<>(List.of(new IamUser(7L, "alex", "MEMBER", true, 4L)));

        @Override
        public Optional<IamUser> findById(long userId) {
            return values.stream().filter(value -> value.userId() == userId).findFirst();
        }

        @Override
        public List<IamUser> search(long afterUserId, int limit, String username, String userType,
                                    Boolean enabled) {
            return List.of();
        }
    }
}
