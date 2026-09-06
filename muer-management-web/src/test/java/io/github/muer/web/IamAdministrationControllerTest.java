package io.github.iamstarter.web;

import io.github.iamstarter.authorization.AuthorizationDecision;
import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationProfile;
import io.github.iamstarter.authorization.AuthorizationProfileRepository;
import io.github.iamstarter.authorization.AuthorizationProfileService;
import io.github.iamstarter.authorization.AuthorizationRequest;
import io.github.iamstarter.authorization.AuthorizationVersionRepository;
import io.github.iamstarter.authorization.AuthorizationVersionService;
import io.github.iamstarter.authorization.PermissionTemplateService;
import io.github.iamstarter.authorization.PermissionTemplateVersion;
import io.github.iamstarter.authorization.PermissionTemplateVersionRepository;
import io.github.iamstarter.authorization.TemplateVersionStatus;
import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.core.model.IamUser;
import io.github.iamstarter.core.model.Identity;
import io.github.iamstarter.core.port.IamUserRepository;
import io.github.iamstarter.core.port.IdentityRepository;
import io.github.iamstarter.authentication.AccountGovernanceService;
import io.github.iamstarter.session.AuthSession;
import io.github.iamstarter.session.SessionRepository;
import io.github.iamstarter.session.SessionService;
import io.github.iamstarter.session.TokenRecord;
import io.github.iamstarter.session.TokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamAdministrationControllerTest {
    @AfterEach
    void clear_security_context() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void template_update_is_authorized_and_delegated_to_the_template_service() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(put("/iam/admin/templates/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId":2,
                                  "versionNumber":1,
                                  "status":"PUBLISHED",
                                  "permissions":["asset.read"]
                                }
                                """))
                .andExpect(status().isNoContent());

        assertEquals(TemplateVersionStatus.PUBLISHED, fixture.templates().require(9L).status());
        assertEquals("iam.admin.template.write", fixture.authorizedRequests().getFirst().permissionCode());
    }

    @Test
    void profile_update_preserves_path_identity_and_increments_the_owners_version() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(put("/iam/admin/profiles/31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileId":31,
                                  "userId":7,
                                  "profileName":"Updated",
                                  "templateVersionId":9,
                                  "clientTypes":["WEB"],
                                  "enabled":true,
                                  "revoked":false,
                                  "scopes":[{"scopeType":"TENANT","scopeRefId":"alpha","accessMode":"WRITE"}]
                                }
                                """))
                .andExpect(status().isNoContent());

        assertEquals("Updated", fixture.profiles().require(31L).profileName());
        assertEquals(5L, fixture.versions().currentVersion(7L));
        assertEquals("iam.admin.profile.write", fixture.authorizedRequests().getFirst().permissionCode());
    }

    @Test
    void scope_replacement_is_independently_authorized_and_invalidates_old_tokens() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(put("/iam/admin/profiles/31/scopes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scopes":[{"scopeType":"BUILDING","scopeRefId":"8","accessMode":"READ"}]}
                                """))
                .andExpect(status().isNoContent());

        assertEquals("BUILDING", fixture.profiles().require(31L).scopes().getFirst().scopeType());
        assertEquals(5L, fixture.versions().currentVersion(7L));
        assertEquals("iam.admin.scope.write", fixture.authorizedRequests().getFirst().permissionCode());
    }

    @Test
    void authorization_version_increment_returns_the_new_version() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(post("/iam/admin/users/7/authorization-version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationVersion").value(5));

        assertEquals("iam.admin.authorization-version.increment",
                fixture.authorizedRequests().getFirst().permissionCode());
    }

    @Test
    void denied_administration_request_does_not_mutate_state() throws Exception {
        authenticate();
        var fixture = fixture(false);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(post("/iam/admin/users/7/authorization-version"))
                .andExpect(status().isForbidden());

        assertEquals(4L, fixture.versions().currentVersion(7L));
    }

    @Test
    void administrator_can_force_revoke_every_session_for_a_user() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(post("/iam/admin/users/7/sessions/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SECURITY_RESPONSE\"}"))
                .andExpect(status().isNoContent());

        assertEquals(List.of(), fixture.sessions().activeForUser(7L));
        assertEquals("iam.admin.session.revoke-user",
                fixture.authorizedRequests().getFirst().permissionCode());
    }

    @Test
    void identity_governance_is_authorized_persisted_and_invalidates_old_tokens() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(put("/iam/admin/identities/identity-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":7,"identityKey":"alex@example.test","domain":"ACCOUNT","enabled":true}
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(get("/iam/admin/users").param("afterUserId", "0").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("alex"));
        assertEquals(5L, fixture.versions().currentVersion(7L));
        assertEquals("alex@example.test", fixture.identities().require("identity-7").identityKey());
    }

    private static void authenticate() {
        var principal = new IamPrincipal(99L, "administrator", "SECURITY", 40L, 10L, "WEB", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static Fixture fixture(boolean allowed) {
        var captured = new ArrayList<AuthorizationRequest>();
        AuthorizationEngine engine = (principal, request) -> {
            captured.add(request);
            return new AuthorizationDecision(allowed, allowed ? "ALLOW" : "DENY", List.of());
        };
        var templates = new Templates(new PermissionTemplateVersion(
                9L, 2L, 1, TemplateVersionStatus.DRAFT, Set.of("asset.read")));
        var profiles = new Profiles(new AuthorizationProfile(
                31L, 7L, "Original", 9L, Set.of("WEB"), true, false, null, null, List.of()));
        var versions = new Versions(Map.of(7L, 4L));
        var versionService = new AuthorizationVersionService(versions);
        var sessions = new Sessions(List.of(activeSession("session-7-a"), activeSession("session-7-b")));
        var sessionService = new SessionService(sessions, new NoOpTokens());
        var users = new GovernanceUsers(new IamUser(7L, "alex", "MEMBER", true, 4L));
        var identities = new GovernanceIdentities();
        var accountService = new AccountGovernanceService(users, identities, versionService);
        var controller = new IamAdministrationController(
                engine,
                new PermissionTemplateService(templates),
                new AuthorizationProfileService(profiles, versionService),
                versionService,
                sessionService,
                accountService);
        return new Fixture(controller, templates, profiles, versions, sessions, identities, captured);
    }

    private record Fixture(IamAdministrationController controller, Templates templates, Profiles profiles,
                           Versions versions, Sessions sessions,
                           GovernanceIdentities identities,
                           List<AuthorizationRequest> authorizedRequests) { }

    private static AuthSession activeSession(String sessionId) {
        return new AuthSession(sessionId, 7L, "WEB", Instant.parse("2026-08-26T00:00:00Z"),
                Instant.parse("2026-08-27T00:00:00Z"), null, null);
    }

    private static final class Sessions implements SessionRepository {
        private final List<AuthSession> values = new ArrayList<>();
        private Sessions(List<AuthSession> initial) { values.addAll(initial); }
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

    private static final class NoOpTokens implements TokenStore {
        public void save(String token, TokenRecord record, Duration ttl) { }
        public Optional<TokenRecord> resolve(String token) { return Optional.empty(); }
        public void revoke(String token) { }
        public void revokeSession(String sessionId) { }
        public void revokeUser(long userId) { }
        public void refreshTtl(String token, Duration ttl) { }
    }

    private static final class GovernanceUsers implements IamUserRepository {
        private final List<IamUser> values = new ArrayList<>();
        private GovernanceUsers(IamUser... initial) { values.addAll(List.of(initial)); }
        public Optional<IamUser> findById(long userId) {
            return values.stream().filter(value -> value.userId() == userId).findFirst();
        }
        public List<IamUser> findPage(long afterUserId, int limit) {
            return values.stream().filter(value -> value.userId() > afterUserId).limit(limit).toList();
        }
        public void save(IamUser user) {
            values.removeIf(value -> value.userId() == user.userId());
            values.add(user);
        }
    }

    private static final class GovernanceIdentities implements IdentityRepository {
        private final List<Identity> values = new ArrayList<>();
        public Optional<Identity> findById(String identityId) {
            return values.stream().filter(value -> value.identityId().equals(identityId)).findFirst();
        }
        public List<Identity> findByUserId(long userId) {
            return values.stream().filter(value -> value.userId() == userId).toList();
        }
        public void save(Identity identity) {
            values.removeIf(value -> value.identityId().equals(identity.identityId()));
            values.add(identity);
        }
    }

    private static final class Templates implements PermissionTemplateVersionRepository {
        private PermissionTemplateVersion version;
        private Templates(PermissionTemplateVersion version) { this.version = version; }
        public PermissionTemplateVersion require(long versionId) { return version; }
        public void save(PermissionTemplateVersion version) { this.version = version; }
    }

    private static final class Profiles implements AuthorizationProfileRepository {
        private AuthorizationProfile profile;
        private Profiles(AuthorizationProfile profile) { this.profile = profile; }
        public AuthorizationProfile require(long profileId) { return profile; }
        public List<AuthorizationProfile> findByUserId(long userId) {
            return profile.userId() == userId ? List.of(profile) : List.of();
        }
        public void save(AuthorizationProfile profile) { this.profile = profile; }
    }

    private static final class Versions implements AuthorizationVersionRepository {
        private final Map<Long, Long> values = new HashMap<>();
        private Versions(Map<Long, Long> initial) { values.putAll(initial); }
        public long currentVersion(long userId) { return values.getOrDefault(userId, 0L); }
        public long increment(long userId) { return values.merge(userId, 1L, Long::sum); }
    }
}
