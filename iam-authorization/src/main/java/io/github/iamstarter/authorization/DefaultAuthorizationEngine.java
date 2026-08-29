package io.github.iamstarter.authorization;

import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.core.model.ResourceScope;
import io.github.iamstarter.core.port.ResourceHierarchyProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 通用授权决策引擎，按固定顺序校验身份域、客户端、授权配置、权限、范围和扩展策略。
 */
public final class DefaultAuthorizationEngine implements AuthorizationEngine {
    private final ResourceHierarchyProvider hierarchy;
    private final Function<IamPrincipal, Set<String>> permissions;
    private final Function<IamPrincipal, List<ResourceScope>> scopes;
    private final List<AuthorizationPolicy> policies;
    private final Function<IamPrincipal, AuthorizationProfile> activeProfileResolver;
    private final Clock clock;

    public DefaultAuthorizationEngine(ResourceHierarchyProvider hierarchy,
                                      Function<IamPrincipal, Set<String>> permissions,
                                      Function<IamPrincipal, List<ResourceScope>> scopes) {
        this(hierarchy, permissions, scopes, List.of());
    }

    public DefaultAuthorizationEngine(ResourceHierarchyProvider hierarchy,
                                      Function<IamPrincipal, Set<String>> permissions,
                                      Function<IamPrincipal, List<ResourceScope>> scopes,
                                      List<AuthorizationPolicy> policies) {
        this(hierarchy, permissions, scopes, policies, null, Clock.systemUTC());
    }

    /**
     * 创建会在运行时验证活动授权配置的授权引擎。
     *
     * @param activeProfileResolver 根据令牌主体读取当前活动授权配置的函数
     * @param clock 用于判断授权配置有效期的时钟
     */
    public DefaultAuthorizationEngine(ResourceHierarchyProvider hierarchy,
                                      Function<IamPrincipal, Set<String>> permissions,
                                      Function<IamPrincipal, List<ResourceScope>> scopes,
                                      Function<IamPrincipal, AuthorizationProfile> activeProfileResolver,
                                      Clock clock) {
        this(hierarchy, permissions, scopes, List.of(), activeProfileResolver, clock);
    }

    /**
     * 创建同时支持授权配置实时校验和扩展策略的授权引擎。
     */
    public DefaultAuthorizationEngine(ResourceHierarchyProvider hierarchy,
                                      Function<IamPrincipal, Set<String>> permissions,
                                      Function<IamPrincipal, List<ResourceScope>> scopes,
                                      List<AuthorizationPolicy> policies,
                                      Function<IamPrincipal, AuthorizationProfile> activeProfileResolver,
                                      Clock clock) {
        this.hierarchy = Objects.requireNonNull(hierarchy);
        this.permissions = Objects.requireNonNull(permissions);
        this.scopes = Objects.requireNonNull(scopes);
        this.policies = List.copyOf(policies);
        this.activeProfileResolver = activeProfileResolver;
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 计算并返回主体对目标资源的可解释授权决策。
     */
    @Override
    public AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request) {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(request, "request must not be null");
        var steps = new ArrayList<AuthorizationDecisionStep>();
        boolean domain = principal.identityDomain().equals(request.domain());
        steps.add(new AuthorizationDecisionStep("IDENTITY_DOMAIN", domain, domain ? "domain matches" : "domain mismatch"));
        if (!domain) return new AuthorizationDecision(false, "IDENTITY_DOMAIN_MISMATCH", steps);
        boolean client = principal.clientType().equals(request.clientType());
        steps.add(new AuthorizationDecisionStep("CLIENT_TYPE", client, client ? "client matches" : "client mismatch"));
        if (!client) return new AuthorizationDecision(false, "CLIENT_TYPE_MISMATCH", steps);
        var profileDecision = validateActiveProfile(principal, steps);
        if (profileDecision != null) return profileDecision;
        boolean permitted = permissions.apply(principal).contains(request.permissionCode());
        steps.add(new AuthorizationDecisionStep("ATOMIC_PERMISSION", permitted, permitted ? "permission granted" : "permission missing"));
        if (!permitted) return new AuthorizationDecision(false, "PERMISSION_DENIED", steps);
        boolean scoped = scopes.apply(principal).stream()
                .filter(scope -> scope.accessMode() == request.scopeAccess())
                .anyMatch(scope -> hierarchy.isWithinScope(request.resource(), scope));
        steps.add(new AuthorizationDecisionStep("RESOURCE_SCOPE", scoped, scoped ? "scope granted" : "scope denied"));
        if (!scoped) return new AuthorizationDecision(false, "SCOPE_DENIED", steps);
        for (var policy : policies) {
            var result = policy.evaluate(principal, request);
            steps.add(new AuthorizationDecisionStep("POLICY:" + result.code(), result.allowed(), result.reason()));
            if (!result.allowed()) return new AuthorizationDecision(false, result.code(), steps);
        }
        return new AuthorizationDecision(true, "ALLOWED", steps);
    }

    /**
     * 校验令牌引用的授权配置仍属于主体且在当前时间和客户端范围内有效。
     */
    private AuthorizationDecision validateActiveProfile(IamPrincipal principal,
                                                         List<AuthorizationDecisionStep> steps) {
        if (activeProfileResolver == null) return null;
        if (principal.activeProfileId() == null) {
            return denyProfile(steps, "PROFILE_MISSING", "active profile missing");
        }
        final AuthorizationProfile profile;
        try {
            profile = activeProfileResolver.apply(principal);
        } catch (RuntimeException exception) {
            return denyProfile(steps, "PROFILE_UNAVAILABLE", "active profile unavailable");
        }
        if (profile == null) return denyProfile(steps, "PROFILE_MISSING", "active profile missing");
        if (profile.profileId() != principal.activeProfileId() || profile.userId() != principal.userId()) {
            return denyProfile(steps, "PROFILE_OWNER_MISMATCH", "profile does not belong to principal");
        }
        if (!profile.enabled()) return denyProfile(steps, "PROFILE_DISABLED", "profile disabled");
        if (profile.revoked()) return denyProfile(steps, "PROFILE_REVOKED", "profile revoked");
        if (!profile.clientTypes().contains(principal.clientType())) {
            return denyProfile(steps, "PROFILE_CLIENT_DENIED", "profile does not allow client");
        }
        Instant now = clock.instant();
        if (profile.validFrom() != null && profile.validFrom().isAfter(now)) {
            return denyProfile(steps, "PROFILE_NOT_YET_VALID", "profile not yet valid");
        }
        if (profile.validUntil() != null && !profile.validUntil().isAfter(now)) {
            return denyProfile(steps, "PROFILE_EXPIRED", "profile expired");
        }
        if (profile.templateVersionId() != principal.templateVersionId()) {
            return denyProfile(steps, "PROFILE_TEMPLATE_MISMATCH", "profile template version changed");
        }
        steps.add(new AuthorizationDecisionStep("ACTIVE_PROFILE", true, "profile valid"));
        return null;
    }

    /**
     * 追加活动授权配置失败步骤并生成拒绝决策。
     */
    private AuthorizationDecision denyProfile(List<AuthorizationDecisionStep> steps, String code, String reason) {
        steps.add(new AuthorizationDecisionStep("ACTIVE_PROFILE", false, reason));
        return new AuthorizationDecision(false, code, steps);
    }
}
