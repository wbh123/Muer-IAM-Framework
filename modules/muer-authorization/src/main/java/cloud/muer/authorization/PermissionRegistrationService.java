package cloud.muer.authorization;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates, deduplicates, and persists application-declared permissions. */
public final class PermissionRegistrationService {
    private final PermissionRepository repository;
    private volatile Set<String> declaredCodes = Set.of();

    public PermissionRegistrationService(PermissionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public synchronized void register(Collection<PermissionDefinitionProvider> providers) {
        Objects.requireNonNull(providers, "providers must not be null");
        Map<String, PermissionDefinition> definitions = new LinkedHashMap<>();
        for (PermissionDefinitionProvider provider : providers) {
            Collection<PermissionDefinition> supplied = Objects.requireNonNull(
                    Objects.requireNonNull(provider, "provider must not be null").getPermissionDefinitions(),
                    "provider definitions must not be null");
            for (PermissionDefinition definition : supplied) {
                PermissionDefinition validated = Objects.requireNonNull(definition, "permission definition must not be null");
                PermissionDefinition previous = definitions.putIfAbsent(validated.code(), validated);
                if (previous != null && !previous.equals(validated)) {
                    throw new IllegalStateException("Conflicting Muer permission definition: " + validated.code());
                }
            }
        }
        definitions.values().forEach(repository::upsert);
        declaredCodes = Set.copyOf(new LinkedHashSet<>(definitions.keySet()));
    }

    public Set<String> declaredCodes() {
        return declaredCodes;
    }
}
