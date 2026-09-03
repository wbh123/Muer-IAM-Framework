package io.github.iamstarter.example;

import io.github.iamstarter.authorization.AuthorizationEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DocumentControllerTest {
    @Test
    void public_health_is_available_and_document_access_requires_an_iam_principal() {
        var controller = new DocumentController(mock(AuthorizationEngine.class));

        assertEquals(200, controller.health().getStatusCode().value());
        assertEquals(401, controller.read("1001").getStatusCode().value());
    }
}
