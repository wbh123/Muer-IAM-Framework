package io.github.muer.authorization;

import io.github.muer.core.model.IamPrincipal;
import io.github.muer.core.model.ResourceDescriptor;
import io.github.muer.core.model.ResourceScope;
import io.github.muer.core.model.ScopeAccess;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证授权引擎在权限、资源范围和活动授权配置变化时的拒绝行为。
 */
class DefaultAuthorizationEngineTest {
    private final IamPrincipal principal = new IamPrincipal(7L, "identity-7", "BUSINESS", 11L, 2L, "WEB", 1L);

    @Test
    void denies_when_request_domain_differs_from_identity_domain() {
        var decision = engine(Set.of("order.approve"), List.of()).decide(principal, request("ACCOUNT", "order.approve"));
        assertFalse(decision.allowed());
    }

    @Test
    void grants_only_when_active_profile_has_permission_and_scope() {
        var scope = new ResourceScope("DEPARTMENT", "7", ScopeAccess.WRITE);
        var decision = engine(Set.of("order.approve"), List.of(scope)).decide(principal, request("BUSINESS", "order.approve"));
        assertTrue(decision.allowed());
    }

    @Test
    void policy_denial_overrides_a_core_grant_and_is_explained() {
        var scope = new ResourceScope("DEPARTMENT", "7", ScopeAccess.WRITE);
        AuthorizationPolicy policy = (resolvedPrincipal, resolvedRequest) ->
                AuthorizationPolicyResult.deny("CHANGE_WINDOW_CLOSED", "changes are not allowed now");
        var engine = new DefaultAuthorizationEngine(
                (resource, candidate) -> "7".equals(candidate.scopeRefId()),
                ignored -> Set.of("order.approve"), ignored -> List.of(scope), List.of(policy));

        var decision = engine.decide(principal, request("BUSINESS", "order.approve"));

        assertFalse(decision.allowed());
        assertEquals("CHANGE_WINDOW_CLOSED", decision.decisionCode());
        assertEquals("POLICY:CHANGE_WINDOW_CLOSED", decision.steps().getLast().code());
    }

    /**
     * 防止已撤销配置仍通过历史权限集合继续访问资源。
     */
    @Test
    void denies_a_revoked_active_profile_even_when_its_template_still_grants_the_permission() {
        var revoked = new AuthorizationProfile(11L, 7L, "operations", 2L, Set.of("WEB"), true, true,
                null, null, List.of(new ResourceScope("DEPARTMENT", "7", ScopeAccess.WRITE)));
        var engine = new DefaultAuthorizationEngine(
                (resource, scope) -> "7".equals(scope.scopeRefId()),
                ignored -> Set.of("order.approve"),
                ignored -> revoked.scopes(),
                ignored -> revoked,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), java.time.ZoneOffset.UTC));

        var decision = engine.decide(principal, request("BUSINESS", "order.approve"));

        assertFalse(decision.allowed());
        assertEquals("PROFILE_REVOKED", decision.decisionCode());
    }

    /**
     * 防止调用方用请求参数替换 Bearer 主体携带的客户端类型。
     */
    @Test
    void denies_a_request_that_forges_a_different_client_type_than_the_bearer_principal() {
        var profile = new AuthorizationProfile(11L, 7L, "operations", 2L, Set.of("WEB"), true, false,
                null, null, List.of(new ResourceScope("DEPARTMENT", "7", ScopeAccess.WRITE)));
        var engine = new DefaultAuthorizationEngine(
                (resource, scope) -> "7".equals(scope.scopeRefId()),
                ignored -> Set.of("order.approve"),
                ignored -> profile.scopes(),
                ignored -> profile,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), java.time.ZoneOffset.UTC));

        var decision = engine.decide(principal, new AuthorizationRequest("order.approve", "BUSINESS", "MOBILE",
                new ResourceDescriptor("ORDER", "o-1", List.of(), java.util.Map.of()), ScopeAccess.WRITE));

        assertFalse(decision.allowed());
        assertEquals("CLIENT_TYPE_MISMATCH", decision.decisionCode());
    }

    private DefaultAuthorizationEngine engine(Set<String> permissions, List<ResourceScope> scopes) {
        return new DefaultAuthorizationEngine((resource, scope) -> "7".equals(scope.scopeRefId()), ignored -> permissions, ignored -> scopes);
    }

    private AuthorizationRequest request(String domain, String permission) {
        return new AuthorizationRequest(permission, domain, "WEB", new ResourceDescriptor("ORDER", "o-1", List.of(), java.util.Map.of()), ScopeAccess.WRITE);
    }
}
