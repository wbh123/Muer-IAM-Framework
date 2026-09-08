package cloud.muer.autoconfigure.web;

import cloud.muer.authorization.PermissionDefinition;
import cloud.muer.authorization.PermissionRegistrationService;
import cloud.muer.authorization.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequirePermissionDefinitionWarningListenerTest {

    @Test
    void reportsOnlyMvcPermissionsMissingFromCurrentProviderDeclarations() throws Exception {
        var registration = new PermissionRegistrationService(new NoOpPermissionRepository());
        registration.register(List.of(() -> List.of(
                new PermissionDefinition("document:read", "Read document", "Read a document"))));
        var listener = new RequirePermissionDefinitionWarningListener(registration);

        assertEquals(Set.of("document:update"), listener.missingCodes(List.of(
                handler("read"), handler("update"))));
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        Method method = ProtectedDocumentController.class.getDeclaredMethod(name);
        return new HandlerMethod(new ProtectedDocumentController(), method);
    }

    private static final class ProtectedDocumentController {
        @RequirePermission("document:read")
        void read() { }

        @RequirePermission("document:update")
        void update() { }
    }

    private static final class NoOpPermissionRepository implements PermissionRepository {
        @Override
        public void upsert(PermissionDefinition definition) { }
    }
}
