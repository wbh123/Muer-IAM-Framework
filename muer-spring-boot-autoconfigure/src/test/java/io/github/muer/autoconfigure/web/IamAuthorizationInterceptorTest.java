package io.github.iamstarter.autoconfigure.web;

import io.github.iamstarter.authorization.AuthorizationDecision;
import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationRequest;
import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.core.model.ResourceDescriptor;
import io.github.iamstarter.core.model.ScopeAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IamAuthorizationInterceptorTest {
    private final IamPrincipal principal = new IamPrincipal(101L, "identity-101", "EXAMPLE", 401L, 301L, "WEB", 1L);
    private final ResourceDescriptor resource = new ResourceDescriptor("DOCUMENT", "1001", List.of("PROJECT:101"), Map.of());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignores_unannotated_handlers() throws Exception {
        var engine = mock(AuthorizationEngine.class);
        var failures = mock(IamAuthorizationFailureHandler.class);
        var interceptor = new IamAuthorizationInterceptor(engine, (request, method) -> Optional.of(resource), failures);

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), unannotatedHandler()));
        verify(engine, never()).decide(any(), any());
        verifyNoInteractions(failures);
    }

    @Test
    void rejects_annotated_handler_without_an_iam_principal() throws Exception {
        var engine = mock(AuthorizationEngine.class);
        var failures = mock(IamAuthorizationFailureHandler.class);
        var interceptor = new IamAuthorizationInterceptor(engine, (request, method) -> Optional.of(resource), failures);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler("read")));
        verify(failures).write(request, response, 401, IamAuthorizationFailure.UNAUTHENTICATED);
        verifyNoInteractions(engine);
    }

    @Test
    void returns_not_found_when_the_host_cannot_resolve_the_resource() throws Exception {
        authenticate();
        var engine = mock(AuthorizationEngine.class);
        var failures = mock(IamAuthorizationFailureHandler.class);
        var interceptor = new IamAuthorizationInterceptor(engine, (request, method) -> Optional.empty(), failures);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler("read")));
        verify(failures).write(request, response, 404, IamAuthorizationFailure.RESOURCE_NOT_FOUND);
        verifyNoInteractions(engine);
    }

    @Test
    void fails_closed_when_an_annotated_handler_has_no_resource_resolver() throws Exception {
        authenticate();
        var engine = mock(AuthorizationEngine.class);
        var failures = mock(IamAuthorizationFailureHandler.class);
        var interceptor = new IamAuthorizationInterceptor(engine, null, failures);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler("read")));
        verify(failures).write(request, response, 500, IamAuthorizationFailure.RESOURCE_RESOLUTION_UNAVAILABLE);
        verifyNoInteractions(engine);
    }

    @Test
    void denies_engine_rejection_and_uses_method_annotation_over_type_annotation() throws Exception {
        authenticate();
        var engine = mock(AuthorizationEngine.class);
        var failures = mock(IamAuthorizationFailureHandler.class);
        when(engine.decide(any(), any())).thenReturn(new AuthorizationDecision(false, "SCOPE_DENIED", List.of()));
        var interceptor = new IamAuthorizationInterceptor(engine, (request, method) -> Optional.of(resource), failures);
        var servletRequest = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(servletRequest, response, handler("update")));
        verify(failures).write(servletRequest, response, 403, IamAuthorizationFailure.ACCESS_DENIED);
        var request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(engine).decide(org.mockito.ArgumentMatchers.eq(principal), request.capture());
        assertEquals("document:update", request.getValue().permissionCode());
        assertEquals(ScopeAccess.WRITE, request.getValue().scopeAccess());
    }

    @Test
    void permits_an_allowed_request() throws Exception {
        authenticate();
        var engine = mock(AuthorizationEngine.class);
        var failures = mock(IamAuthorizationFailureHandler.class);
        when(engine.decide(any(), any())).thenReturn(new AuthorizationDecision(true, "ALLOWED", List.of()));
        var interceptor = new IamAuthorizationInterceptor(engine, (request, method) -> Optional.of(resource), failures);

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler("read")));
        verifyNoInteractions(failures);
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new ExampleHandler(), ExampleHandler.class.getDeclaredMethod(methodName));
    }

    @RequirePermission("document:read")
    static final class ExampleHandler {
        void read() { }

        @RequirePermission(value = "document:update", access = ScopeAccess.WRITE)
        void update() { }
    }

    static final class UnannotatedHandler {
        void publicEndpoint() { }
    }

    private static HandlerMethod unannotatedHandler() throws NoSuchMethodException {
        return new HandlerMethod(new UnannotatedHandler(), UnannotatedHandler.class.getDeclaredMethod("publicEndpoint"));
    }
}
