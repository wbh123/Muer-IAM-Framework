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
        var interceptor = new IamAuthorizationInterceptor(engine, (request, method) -> Optional.of(resource));

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), unannotatedHandler()));
        verify(engine, never()).decide(any(), any());
    }

    @Test
    void rejects_annotated_handler_without_an_iam_principal() throws Exception {
        var interceptor = new IamAuthorizationInterceptor(mock(AuthorizationEngine.class), (request, method) -> Optional.of(resource));
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, handler("read")));
        assertEquals(401, response.getStatus());
    }

    @Test
    void fails_closed_when_the_host_cannot_resolve_the_resource() throws Exception {
        authenticate();
        var interceptor = new IamAuthorizationInterceptor(mock(AuthorizationEngine.class), (request, method) -> Optional.empty());
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, handler("read")));
        assertEquals(500, response.getStatus());
    }

    @Test
    void denies_engine_rejection_and_uses_method_annotation_over_type_annotation() throws Exception {
        authenticate();
        var engine = mock(AuthorizationEngine.class);
        when(engine.decide(any(), any())).thenReturn(new AuthorizationDecision(false, "SCOPE_DENIED", List.of()));
        var interceptor = new IamAuthorizationInterceptor(engine, (request, method) -> Optional.of(resource));
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, handler("update")));
        assertEquals(403, response.getStatus());
        var request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(engine).decide(org.mockito.ArgumentMatchers.eq(principal), request.capture());
        assertEquals("document:update", request.getValue().permissionCode());
        assertEquals(ScopeAccess.WRITE, request.getValue().scopeAccess());
    }

    @Test
    void permits_an_allowed_request() throws Exception {
        authenticate();
        var engine = mock(AuthorizationEngine.class);
        when(engine.decide(any(), any())).thenReturn(new AuthorizationDecision(true, "ALLOWED", List.of()));
        var interceptor = new IamAuthorizationInterceptor(engine, (request, method) -> Optional.of(resource));

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler("read")));
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
