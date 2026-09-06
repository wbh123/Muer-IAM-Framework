package io.github.muer.autoconfigure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IamAuthorizationFailureHandlerTest {
    @Test
    void exposes_stable_failure_codes_and_host_handler_signature() throws Exception {
        assertEquals("IAM_UNAUTHENTICATED", IamAuthorizationFailure.UNAUTHENTICATED.code());
        assertEquals("IAM_ACCESS_DENIED", IamAuthorizationFailure.ACCESS_DENIED.code());
        assertEquals("IAM_RESOURCE_NOT_FOUND", IamAuthorizationFailure.RESOURCE_NOT_FOUND.code());
        assertEquals("IAM_RESOURCE_RESOLUTION_UNAVAILABLE",
                IamAuthorizationFailure.RESOURCE_RESOLUTION_UNAVAILABLE.code());
        assertEquals(void.class, IamAuthorizationFailureHandler.class.getMethod("write",
                HttpServletRequest.class, HttpServletResponse.class, int.class,
                IamAuthorizationFailure.class).getReturnType());
        assertEquals(IOException.class, IamAuthorizationFailureHandler.class.getMethod("write",
                HttpServletRequest.class, HttpServletResponse.class, int.class,
                IamAuthorizationFailure.class).getExceptionTypes()[0]);
    }

    @Test
    void writes_a_safe_problem_detail_response() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/documents/1001");
        var response = new MockHttpServletResponse();

        new ProblemDetailIamAuthorizationFailureHandler(new ObjectMapper()).write(
                request, response, 403, IamAuthorizationFailure.ACCESS_DENIED);

        assertEquals(403, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertTrue(response.getContentAsString().contains("IAM_ACCESS_DENIED"));
        assertTrue(response.getContentAsString().contains("/api/documents/1001"));
        assertFalse(response.getContentAsString().contains("document:read"));
        assertFalse(response.getContentAsString().contains("opaque-token"));
    }
}
