package io.github.muer.authorization;

import io.github.muer.core.model.ResourceScope;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record AuthorizationProfile(
        long profileId,
        long userId,
        String profileName,
        long templateVersionId,
        Set<String> clientTypes,
        boolean enabled,
        boolean revoked,
        Instant validFrom,
        Instant validUntil,
        List<ResourceScope> scopes) {
    public AuthorizationProfile {
        if (profileId <= 0 || userId <= 0 || templateVersionId <= 0) {
            throw new IllegalArgumentException("profile identifiers must be positive");
        }
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("profileName must not be blank");
        }
        clientTypes = clientTypes == null ? Set.of() : Set.copyOf(clientTypes);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public AuthorizationProfile withScopes(List<ResourceScope> replacement) {
        return new AuthorizationProfile(profileId, userId, profileName, templateVersionId,
                clientTypes, enabled, revoked, validFrom, validUntil, replacement);
    }
}
