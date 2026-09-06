package io.github.muer.autoconfigure;

import io.github.muer.authorization.PermissionDefinition;
import io.github.muer.authorization.PermissionDefinitionProvider;
import io.github.muer.authorization.PermissionRegistrationService;
import io.github.muer.authorization.PermissionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionDefinitionRegistrationListenerTest {

    @Test
    void registersAllProvidersWhenApplicationIsReady() {
        var repository = new RecordingPermissionRepository();
        var listener = new PermissionDefinitionRegistrationListener(
                new PermissionRegistrationService(repository),
                List.of(() -> List.of(new PermissionDefinition("document:read", "Read document", "Read a document"))));

        listener.onApplicationReady();

        assertEquals(List.of("document:read"), repository.codes());
    }

    private static final class RecordingPermissionRepository implements PermissionRepository {
        private final List<String> codes = new ArrayList<>();

        @Override
        public void upsert(PermissionDefinition definition) {
            codes.add(definition.code());
        }

        List<String> codes() {
            return List.copyOf(codes);
        }
    }
}
