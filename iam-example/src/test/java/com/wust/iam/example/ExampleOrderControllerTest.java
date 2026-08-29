package com.wust.iam.example;

import com.wust.iam.authorization.AuthorizationDecision;
import com.wust.iam.authorization.AuthorizationEngine;
import com.wust.iam.authorization.AuthorizationRequest;
import com.wust.iam.core.model.IamPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExampleOrderControllerTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reads_an_order_through_atomic_permission_and_department_scope() {
        authenticate();
        var engine = mock(AuthorizationEngine.class);
        when(engine.decide(any(), any())).thenReturn(new AuthorizationDecision(true, "ALLOWED", List.of()));
        var controller = new ExampleOrderController(engine);

        var response = controller.getOrder("9001");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("501", response.getBody().departmentId());
        var request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(engine).decide(any(), request.capture());
        assertEquals("order.read", request.getValue().permissionCode());
        assertEquals("ORDER", request.getValue().resource().resourceType());
        assertEquals(List.of("DEPARTMENT:501"), request.getValue().resource().parentPath());
    }

    @Test
    void denies_a_request_when_the_runtime_engine_denies_it() {
        authenticate();
        var engine = mock(AuthorizationEngine.class);
        when(engine.decide(any(), any())).thenReturn(new AuthorizationDecision(false, "SCOPE_DENIED", List.of()));

        assertEquals(403, new ExampleOrderController(engine).getOrder("9001").getStatusCode().value());
    }

    @Test
    void rejects_a_request_without_an_iam_principal() {
        assertEquals(401, new ExampleOrderController(mock(AuthorizationEngine.class))
                .getOrder("9001").getStatusCode().value());
    }

    private static void authenticate() {
        var principal = new IamPrincipal(101L, "example-user", "EXAMPLE", 401L, 301L, "WEB", 1L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
