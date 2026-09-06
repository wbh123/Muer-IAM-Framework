package io.github.muer.web;

import io.github.muer.authorization.AuthorizationEngine;
import io.github.muer.authorization.AuthorizationRequest;
import io.github.muer.core.model.IamPrincipal;
import io.github.muer.core.model.ResourceDescriptor;
import io.github.muer.core.model.ScopeAccess;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

/**
 * Shared authorization plumbing for IAM management controllers. Every admin
 * endpoint must be re-checked against the {@link AuthorizationEngine}; this class
 * only reduces boilerplate and never introduces role-based bypasses.
 */
public final class WebSecurity {
    private WebSecurity() {
    }

    public static IamPrincipal currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IamPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public static boolean allowed(AuthorizationEngine engine, IamPrincipal principal,
                                  String permissionCode, String resourceType, String resourceId,
                                  ScopeAccess access) {
        if (engine == null || principal == null) return false;
        var request = new AuthorizationRequest(
                permissionCode,
                principal.identityDomain(),
                principal.clientType(),
                new ResourceDescriptor(resourceType, resourceId, List.of(), Map.of()),
                access);
        return engine.decide(principal, request).allowed();
    }

    public static boolean allowedRead(AuthorizationEngine engine, IamPrincipal principal,
                                      String permissionCode, String resourceType, String resourceId) {
        return allowed(engine, principal, permissionCode, resourceType, resourceId, ScopeAccess.READ);
    }
}
