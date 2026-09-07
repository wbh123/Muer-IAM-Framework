package io.github.muer.example;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DocumentControllerTest {
    @Test
    void public_health_and_document_lookup_are_host_owned() {
        var documents = new DocumentCatalog();
        documents.seedQuickStartDocuments();
        var controller = new DocumentController(documents);

        assertEquals(200, controller.health().getStatusCode().value());
        assertEquals(200, controller.read("1001").getStatusCode().value());
        assertEquals(404, controller.read("missing").getStatusCode().value());
        assertFalse(Arrays.stream(DocumentController.class.getConstructors())
                .anyMatch(constructor -> Arrays.asList(constructor.getParameterTypes())
                        .contains(io.github.muer.authorization.AuthorizationEngine.class)));
    }
}
