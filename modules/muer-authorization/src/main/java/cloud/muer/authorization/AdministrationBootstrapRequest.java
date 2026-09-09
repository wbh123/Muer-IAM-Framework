package cloud.muer.authorization;

import java.util.Objects;
import java.util.Set;

/** Explicit host-controlled input for first Muer administrator provisioning. */
public record AdministrationBootstrapRequest(long userId, Set<String> clientTypes) {
    public AdministrationBootstrapRequest {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        clientTypes = clientTypes == null ? Set.of() : Set.copyOf(clientTypes);
        if (clientTypes.isEmpty()) throw new IllegalArgumentException("clientTypes must not be empty");
        if (clientTypes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("clientTypes must not contain blanks");
        }
    }
}
