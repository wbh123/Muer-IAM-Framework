package io.github.iamstarter.web;

import io.github.iamstarter.authorization.AuthorizationDecision;
import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationProfile;
import io.github.iamstarter.authorization.AuthorizationProfileQueryRepository;
import io.github.iamstarter.authorization.AuthorizationProfileRepository;
import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.core.model.IamUser;
import io.github.iamstarter.core.model.ResourceScope;
import io.github.iamstarter.core.model.ScopeAccess;
import io.github.iamstarter.core.port.UserQueryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamManagementProfilesControllerTest {
    private static final AuthorizationProfile PROFILE = new AuthorizationProfile(31L, 7L, "Operator",
            9L, Set.of("WEB"), true, false, Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2027-01-01T00:00:00Z"),
            List.of(new ResourceScope("PROJECT", "101", ScopeAccess.READ)));

    @AfterEach
    void clear_security_context() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profile_listing_is_filtered_and_paginated() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(get("/iam/admin/profiles").param("enabled", "true").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].profileId").value(31))
                .andExpect(jsonPath("$.items[0].scopes[0].scopeRefId").value("101"))
                .andExpect(jsonPath("$.items[0].validFrom").isNotEmpty())
                .andExpect(jsonPath("$.items[0].validUntil").isNotEmpty())
                .andExpect(jsonPath("$.nextAfterProfileId").value(31));
    }

    @Test
    void profile_detail_and_scopes_require_profile_read_permission() throws Exception {
        authenticate();
        var fixture = fixture(false);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/profiles/31")).andExpect(status().isForbidden());
        mvc.perform(get("/iam/admin/profiles/31/scopes")).andExpect(status().isForbidden());
    }

    @Test
    void profile_detail_and_scopes_are_not_found_for_unknown_profiles() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/profiles/999")).andExpect(status().isNotFound());
        mvc.perform(get("/iam/admin/profiles/999/scopes")).andExpect(status().isNotFound());
    }

    @Test
    void listing_profiles_of_an_unknown_user_is_not_found() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/users/999/profiles")).andExpect(status().isNotFound());
    }

    private static void authenticate() {
        var principal = new IamPrincipal(99L, "administrator", "SECURITY", 40L, 10L, "WEB", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static Fixture fixture(boolean allowed) {
        AuthorizationEngine engine = (principal, request) ->
                new AuthorizationDecision(allowed, allowed ? "ALLOW" : "DENY", List.of());
        return new Fixture(new IamManagementProfilesController(engine, new FakeProfileQuery(),
                new FakeProfileRepository(), new FakeUsers()));
    }

    private record Fixture(IamManagementProfilesController controller) {
    }

    private static final class FakeProfileQuery implements AuthorizationProfileQueryRepository {
        @Override
        public List<AuthorizationProfile> search(long afterProfileId, int limit, Long userId,
                                                 Long templateVersionId, Boolean enabled,
                                                 Boolean revoked, String clientType) {
            boolean matches = (userId == null || userId == 7L)
                    && (enabled == null || enabled == PROFILE.enabled());
            return matches && PROFILE.profileId() > afterProfileId
                    ? List.of(PROFILE).subList(0, Math.min(1, limit)) : List.of();
        }
    }

    private static final class FakeProfileRepository implements AuthorizationProfileRepository {
        @Override
        public AuthorizationProfile require(long profileId) {
            if (profileId != PROFILE.profileId()) throw new java.util.NoSuchElementException("missing");
            return PROFILE;
        }

        @Override
        public List<AuthorizationProfile> findByUserId(long userId) {
            return userId == PROFILE.userId() ? List.of(PROFILE) : List.of();
        }

        @Override
        public void save(AuthorizationProfile profile) {
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
