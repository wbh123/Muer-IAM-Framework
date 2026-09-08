package cloud.muer.web;

import cloud.muer.authorization.AuthorizationDecision;
import cloud.muer.authorization.AuthorizationDecisionStep;
import cloud.muer.authorization.AuthorizationRequest;
import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.authorization.AuthorizationProfileRepository;
import cloud.muer.authorization.AuthorizationProfileService;
import cloud.muer.authorization.AuthorizationVersionRepository;
import cloud.muer.authorization.AuthorizationVersionService;
import cloud.muer.authentication.AuthenticationService;
import cloud.muer.authentication.AuthorizationProfileSwitchService;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.ScopeAccess;
import cloud.muer.diagnostics.AuthorizationDiagnosticsService;
import cloud.muer.session.TokenRecord;
import cloud.muer.session.TokenStore;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamAuthorizationControllerTest {
    @AfterEach
    void clear_security_context() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void diagnostics_uses_the_authenticated_principal_and_projects_the_runtime_decision() throws Exception {
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 31L, 9L, "WEB", 4L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var capturedPrincipal = new AtomicReference<IamPrincipal>();
        var capturedRequest = new AtomicReference<AuthorizationRequest>();
        var diagnostics = new AuthorizationDiagnosticsService((actualPrincipal, actualRequest) -> {
            capturedPrincipal.set(actualPrincipal);
            capturedRequest.set(actualRequest);
            return new AuthorizationDecision(false, "SCOPE_DENIED",
                    List.of(new AuthorizationDecisionStep("scope", false, "outside assigned scope")));
        });
        var mvc = MockMvcBuilders.standaloneSetup(controller(diagnostics, List.of())).build();

        mvc.perform(post("/iam/authorization/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCode":"asset.read",
                                  "domain":"SECURITY",
                                  "clientType":"WEB",
                                  "resourceType":"ASSET",
                                  "resourceId":"asset-3",
                                  "scopeAccess":"READ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.decisionCode").value("SCOPE_DENIED"))
                .andExpect(jsonPath("$.steps[0].code").value("scope"))
                .andExpect(jsonPath("$.steps[0].passed").value(false));

        assertEquals(7L, capturedPrincipal.get().userId());
        assertEquals("asset.read", capturedRequest.get().permissionCode());
        assertEquals("asset-3", capturedRequest.get().resource().resourceId());
    }

    @Test
    void diagnostics_requires_an_iam_security_context() throws Exception {
        var diagnostics = new AuthorizationDiagnosticsService((principal, request) ->
                new AuthorizationDecision(true, "ALLOW", List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(controller(diagnostics, List.of())).build();

        mvc.perform(post("/iam/authorization/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCode":"asset.read",
                                  "domain":"SECURITY",
                                  "clientType":"WEB",
                                  "resourceType":"ASSET",
                                  "resourceId":"asset-3",
                                  "scopeAccess":"READ"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profile_listing_returns_only_profiles_available_to_the_authenticated_principal() throws Exception {
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 31L, 9L, "WEB", 4L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var available = new AuthorizationProfile(31L, 7L, "Operator", 9L, Set.of("WEB"), true, false,
                null, null, List.of(new ResourceScope("TENANT", "alpha", ScopeAccess.WRITE)));
        var unavailable = new AuthorizationProfile(32L, 7L, "Mobile", 9L, Set.of("MOBILE"), true, false,
                null, null, List.of());
        var diagnostics = new AuthorizationDiagnosticsService((actualPrincipal, request) ->
                new AuthorizationDecision(true, "ALLOW", List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(
                controller(diagnostics, List.of(available, unavailable))).build();

        mvc.perform(get("/iam/authorization/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].profileId").value(31))
                .andExpect(jsonPath("$[0].profileName").value("Operator"))
                .andExpect(jsonPath("$[0].scopes[0].scopeType").value("TENANT"))
                .andExpect(jsonPath("$[0].scopes[0].accessMode").value("WRITE"));
    }

    @Test
    void switching_profile_returns_a_new_token_bound_to_the_selected_profile() throws Exception {
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 19L, 3L, "WEB", 4L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var target = new AuthorizationProfile(31L, 7L, "Operator", 9L, Set.of("WEB"), true, false,
                null, null, List.of());
        var diagnostics = new AuthorizationDiagnosticsService((actualPrincipal, request) ->
                new AuthorizationDecision(true, "ALLOW", List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(controller(diagnostics, List.of(target))).build();

        mvc.perform(post("/iam/authorization/profiles/31/switch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("switched-token"))
                .andExpect(jsonPath("$.principal.activeProfileId").value(31))
                .andExpect(jsonPath("$.principal.templateVersionId").value(9))
                .andExpect(jsonPath("$.principal.authorizationVersion").value(5));
    }

    @Test
    void switching_to_an_unavailable_profile_returns_not_found() throws Exception {
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 19L, 3L, "WEB", 4L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var diagnostics = new AuthorizationDiagnosticsService((actualPrincipal, request) ->
                new AuthorizationDecision(true, "ALLOW", List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(controller(diagnostics, List.of())).build();

        mvc.perform(post("/iam/authorization/profiles/99/switch"))
                .andExpect(status().isNotFound());
    }

    @Test
    void diagnostics_is_self_service_and_does_not_require_the_admin_diagnostics_permission() throws Exception {
        // A reader without iam.admin.diagnostics must still be able to project its
        // OWN decision (200), never a 403: this is the 0.1.0 public contract.
        var principal = new IamPrincipal(7L, "identity-7", "SECURITY", 31L, 9L, "WEB", 4L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var capturedPrincipal = new AtomicReference<IamPrincipal>();
        var capturedRequest = new AtomicReference<AuthorizationRequest>();
        var diagnostics = new AuthorizationDiagnosticsService((actualPrincipal, request) -> {
            capturedPrincipal.set(actualPrincipal);
            capturedRequest.set(request);
            return new AuthorizationDecision(false, "SCOPE_DENIED",
                    List.of(new AuthorizationDecisionStep("scope", false, "outside assigned scope")));
        });
        // No AuthorizationEngine is wired at all - the endpoint must not consult one.
        var mvc = MockMvcBuilders.standaloneSetup(controller(diagnostics, List.of())).build();

        mvc.perform(post("/iam/authorization/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCode":"asset.read",
                                  "domain":"SECURITY",
                                  "clientType":"WEB",
                                  "resourceType":"ASSET",
                                  "resourceId":"asset-3",
                                  "scopeAccess":"READ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.decisionCode").value("SCOPE_DENIED"))
                .andExpect(jsonPath("$.steps[0].code").value("scope"))
                .andExpect(jsonPath("$.steps[0].passed").value(false));

        // The projected decision must always belong to the SecurityContext principal.
        assertEquals(7L, capturedPrincipal.get().userId());
        assertEquals("asset.read", capturedRequest.get().permissionCode());
        assertEquals("asset-3", capturedRequest.get().resource().resourceId());
    }

    private static IamAuthorizationController controller(AuthorizationDiagnosticsService diagnostics,
                                                         List<AuthorizationProfile> initial) {
        var profiles = profileService(initial);
        return new IamAuthorizationController(diagnostics, profiles, switchService(profiles));
    }

    private static AuthorizationProfileSwitchService switchService(AuthorizationProfileService profiles) {
        TokenStore tokens = new TokenStore() {
            public Optional<TokenRecord> resolve(String token) { return Optional.empty(); }
            public void save(String token, TokenRecord record, Duration ttl) { }
            public void revoke(String token) { }
            public void revokeSession(String sessionId) { }
            public void revokeUser(long userId) { }
            public void refreshTtl(String token, Duration ttl) { }
        };
        var authentication = new AuthenticationService(tokens, userId -> 5L, request -> Optional.empty(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC), Duration.ofHours(8),
                () -> "switched-token", () -> "switched-session");
        return new AuthorizationProfileSwitchService(profiles, authentication);
    }

    private static AuthorizationProfileService profileService(List<AuthorizationProfile> initial) {
        var repository = new AuthorizationProfileRepository() {
            private final List<AuthorizationProfile> profiles = new ArrayList<>(initial);
            public AuthorizationProfile require(long profileId) {
                return profiles.stream().filter(profile -> profile.profileId() == profileId).findFirst().orElseThrow();
            }
            public List<AuthorizationProfile> findByUserId(long userId) {
                return profiles.stream().filter(profile -> profile.userId() == userId).toList();
            }
            public void save(AuthorizationProfile profile) { profiles.add(profile); }
        };
        AuthorizationVersionRepository versions = new AuthorizationVersionRepository() {
            public long currentVersion(long userId) { return 4L; }
            public long increment(long userId) { return 5L; }
        };
        return new AuthorizationProfileService(repository, new AuthorizationVersionService(versions),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));
    }
}
