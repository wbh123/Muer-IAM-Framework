package io.github.iamstarter.autoconfigure.web;

import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationRequest;
import io.github.iamstarter.core.model.IamPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Objects;

/** Delegates declaratively protected MVC handlers to the IAM authorization engine. */
public final class IamAuthorizationInterceptor implements HandlerInterceptor {
    private final AuthorizationEngine authorization;
    @Nullable
    private final MvcResourceDescriptorResolver resources;

    public IamAuthorizationInterceptor(AuthorizationEngine authorization,
                                       @Nullable MvcResourceDescriptorResolver resources) {
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.resources = resources;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method)) return true;
        var requirement = requirement(method);
        if (requirement == null) return true;
        var principal = principal();
        if (principal == null) return deny(response, HttpServletResponse.SC_UNAUTHORIZED);
        if (resources == null) return deny(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        var resource = resources.resolve(request, method);
        if (resource.isEmpty()) return deny(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        var decision = authorization.decide(principal, new AuthorizationRequest(
                requirement.value(), principal.identityDomain(), principal.clientType(),
                resource.orElseThrow(), requirement.access()));
        return decision.allowed() || deny(response, HttpServletResponse.SC_FORBIDDEN);
    }

    @Nullable
    private static RequirePermission requirement(HandlerMethod method) {
        var onMethod = method.getMethodAnnotation(RequirePermission.class);
        return onMethod != null ? onMethod : method.getBeanType().getAnnotation(RequirePermission.class);
    }

    @Nullable
    private static IamPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof IamPrincipal principal ? principal : null;
    }

    private static boolean deny(HttpServletResponse response, int status) throws IOException {
        response.sendError(status);
        return false;
    }
}
