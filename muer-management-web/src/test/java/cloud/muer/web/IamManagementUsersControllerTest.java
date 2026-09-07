package io.github.muer.web;

import io.github.muer.authorization.AuthorizationDecision;
import io.github.muer.authorization.AuthorizationEngine;
import io.github.muer.authorization.AuthorizationRequest;
import io.github.muer.core.model.IamPrincipal;
import io.github.muer.core.model.IamUser;
import io.github.muer.core.model.Identity;
import io.github.muer.core.port.IdentityRepository;
import io.github.muer.core.port.UserQueryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamManagementUsersControllerTest {
    @AfterEach
    void clear_security_context() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void user_detail_requires_authentication_and_the_read_permission() throws Exception {
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(get("/iam/admin/users/7")).andExpect(status().isUnauthorized());

        authenticate();
        mvc.perform(get("/iam/admin/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.username").value("alex"))
                .andExpect(jsonPath("$.authorizationVersion").value(4));
    }

    @Test
    void user_detail_is_rejected_without_the_read_permission() throws Exception {
        authenticate();
        var fixture = fixture(false);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/users/7")).andExpect(status().isForbidden());
    }

    @Test
    void user_detail_returns_not_found_for_an_unknown_user() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/users/999")).andExpect(status().isNotFound());
    }

    @Test
    void identity_detail_is_returned_to_an_authorized_reader() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/identities/identity-7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value("identity-7"))
                .andExpect(jsonPath("$.identityKey").value("alex@example.test"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    private static void authenticate() {
        var principal = new IamPrincipal(99L, "administrator", "SECURITY", 40L, 10L, "WEB", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static Fixture fixture(boolean allowed) {
        AuthorizationEngine engine = (principal, request) ->
                new AuthorizationDecision(allowed, allowed ? "ALLOW" : "DENY", List.of());
        var users = new FakeUsers(new IamUser(7L, "alex", "MEMBER", true, 4L));
        var identities = new FakeIdentities(
                new Identity("identity-7", 7L, "alex@example.test", "ACCOUNT", true));
        return new Fixture(new IamManagementUsersController(engine, users, identities));
    }

    private record Fixture(IamManagementUsersController controller) {
    }

    private static final class FakeUsers implements UserQueryRepository {
        private final List<IamUser> values = new ArrayList<>();

        private FakeUsers(IamUser... initial) {
            values.addAll(List.of(initial));
        }

        @Override
        public Optional<IamUser> findById(long userId) {
            return values.stream().filter(value -> value.userId() == userId).findFirst();
        }

        @Override
        public List<IamUser> search(long afterUserId, int limit, String username, String userType,
                                    Boolean enabled) {
            return values.stream().filter(value -> value.userId() > afterUserId).limit(limit).toList();
        }
    }

    private static final class FakeIdentities implements IdentityRepository {
        private final List<Identity> values = new ArrayList<>();

        private FakeIdentities(Identity... initial) {
            values.addAll(List.of(initial));
        }

        @Override
        public Optional<Identity> findById(String identityId) {
            return values.stream().filter(value -> value.identityId().equals(identityId)).findFirst();
        }

        @Override
        public List<Identity> findByUserId(long userId) {
            return values.stream().filter(value -> value.userId() == userId).toList();
        }

        @Override
        public void save(Identity identity) {
            values.removeIf(value -> value.identityId().equals(identity.identityId()));
            values.add(identity);
        }
    }
}
