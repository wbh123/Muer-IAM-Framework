package io.github.muer.authorization;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionRegistrationServiceTest {

    @Test
    void registersOneDefinitionFromProvider() {
        var repository = new RecordingPermissionRepository();
        var service = new PermissionRegistrationService(repository);

        service.register(List.of(() -> List.of(new PermissionDefinition(
                "document:read", "Read document", "Read a document"))));

        assertEquals(List.of(new PermissionDefinition("document:read", "Read document", "Read a document")),
                repository.upserts());
    }

    @Test
    void acceptsZeroProviders() {
        var repository = new RecordingPermissionRepository();

        assertDoesNotThrow(() -> new PermissionRegistrationService(repository).register(List.of()));

        assertEquals(List.of(), repository.upserts());
    }

    @Test
    void deduplicatesIdenticalDefinitions() {
        var repository = new RecordingPermissionRepository();
        var definition = new PermissionDefinition("order:item:approve", "Approve item", "Approve an order item");

        new PermissionRegistrationService(repository).register(List.of(
                () -> List.of(definition), () -> List.of(definition)));

        assertEquals(List.of(definition), repository.upserts());
    }

    @Test
    void rejectsConflictingDefinitionsForOneCode() {
        var service = new PermissionRegistrationService(new RecordingPermissionRepository());

        var exception = assertThrows(IllegalStateException.class, () -> service.register(List.of(
                () -> List.of(new PermissionDefinition("order:read", "Read orders", "Read orders")),
                () -> List.of(new PermissionDefinition("order:read", "View orders", "View orders")))));

        assertEquals("Conflicting Muer permission definition: order:read", exception.getMessage());
    }

    @Test
    void normalizesWhitespaceWithoutRestrictingPunctuation() {
        var repository = new RecordingPermissionRepository();

        new PermissionRegistrationService(repository).register(List.of(() -> List.of(
                new PermissionDefinition(" finance.report:export ", " Export report ", " Export a report "))));

        assertEquals(List.of(new PermissionDefinition("finance.report:export", "Export report", "Export a report")),
                repository.upserts());
    }

    @Test
    void rejectsBlankAndControlCharacterValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new PermissionDefinition(" ", "Read", "Read a document"));
        assertThrows(IllegalArgumentException.class,
                () -> new PermissionDefinition("document:read", "Read\nDocument", "Read a document"));
        assertThrows(IllegalArgumentException.class,
                () -> new PermissionDefinition("document:read", "Read", "\u0000"));
    }

    private static final class RecordingPermissionRepository implements PermissionRepository {
        private final List<PermissionDefinition> upserts = new ArrayList<>();

        @Override
        public void upsert(PermissionDefinition definition) {
            upserts.add(definition);
        }

        List<PermissionDefinition> upserts() {
            return List.copyOf(upserts);
        }
    }
}
