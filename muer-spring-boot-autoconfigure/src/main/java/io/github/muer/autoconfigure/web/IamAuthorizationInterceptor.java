package io.github.muer.autoconfigure.web;

import io.github.muer.authorization.AuthorizationEngine;
import io.github.muer.authorization.AuthorizationRequest;
import io.github.muer.core.model.IamPrincipal;
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
    private final IamAuthorizationFailureHandler failures;

    public IamAuthorizationInterceptor(AuthorizationEngine authorization,
                                       @Nullable MvcResourceDescriptorResolver resources,
                                       IamAuthorizationFailureHandler failures) {
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.resources = resources;
        this.failures = Objects.requireNonNull(failures, "failures must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method)) return true;
        var requirement = requirement(method);
        if (requirement == null) return true;
        var principal = principal();
        if (principal == null) return deny(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                IamAuthorizationFailure.UNAUTHENTICATED);
        if (resources == null) return deny(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                IamAuthorizationFailure.RESOURCE_RESOLUTION_UNAVAILABLE);
        var resource = resources.resolve(request, method);
        if (resource.isEmpty()) return deny(request, response, HttpServletResponse.SC_NOT_FOUND,
                IamAuthorizationFailure.RESOURCE_NOT_FOUND);
        var decision = authorization.decide(principal, new AuthorizationRequest(
                requirement.value(), principal.identityDomain(), principal.clientType(),
                resource.orElseThrow(), requirement.access()));
        return decision.allowed() || deny(request, response, HttpServletResponse.SC_FORBIDDEN,
                IamAuthorizationFailure.ACCESS_DENIED);
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

    private boolean deny(HttpServletRequest request, HttpServletResponse response,
                         int status, IamAuthorizationFailure failure) throws IOException {
        failures.write(request, response, status, failure);
        return false;
    }
}
