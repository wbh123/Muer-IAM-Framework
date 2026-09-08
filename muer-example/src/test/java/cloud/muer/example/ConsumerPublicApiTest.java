package cloud.muer.example;

import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.ScopeAccess;
import cloud.muer.authentication.LoginRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerPublicApiTest {
    @Test
    void host_user_and_document_project_scope_use_only_public_iam_types() {
        assertTrue(new AppUser(101L, "author-a", "demo-pass", true, "Author A").enabled());
        assertTrue(new ExampleResourceHierarchyAdapter().isWithinScope(
                new ResourceDescriptor("DOCUMENT", "1001", List.of("PROJECT:101", "DEPARTMENT:10"), Map.of()),
                new ResourceScope("PROJECT", "101", ScopeAccess.READ)));
    }

    @Test
    void enabled_host_user_projects_to_a_public_iam_principal() {
        var result = new ExampleIdentityAdapter().exampleIdentityAuthenticator()
                .authenticate(new LoginRequest("author-a", "demo-pass", "WEB"));

        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().identityId().startsWith("app-user-"));
    }
}
