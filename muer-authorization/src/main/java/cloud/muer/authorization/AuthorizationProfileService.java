package cloud.muer.authorization;

import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.IamPrincipal;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class AuthorizationProfileService {
    private final AuthorizationProfileRepository profiles;
    private final AuthorizationVersionService versions;
    private final Clock clock;
    private final AuthorizationScopeMutation scopeMutation;

    public AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions) {
        this(profiles, versions, Clock.systemUTC(), null);
    }

    public AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions,
                                       Clock clock) {
        this(profiles, versions, clock, null);
    }

    public AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions,
                                       AuthorizationScopeMutation scopeMutation) {
        this(profiles, versions, Clock.systemUTC(), Objects.requireNonNull(scopeMutation));
    }

    private AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions,
                                        Clock clock, AuthorizationScopeMutation scopeMutation) {
        this.profiles = Objects.requireNonNull(profiles);
        this.versions = Objects.requireNonNull(versions);
        this.clock = Objects.requireNonNull(clock);
        this.scopeMutation = scopeMutation;
    }

    public void replaceScopes(long profileId, List<ResourceScope> scopes) {
        if (scopeMutation != null) {
            scopeMutation.replaceScopesAndIncrementVersion(profileId, scopes);
            return;
        }
        var profile = profiles.require(profileId);
        profiles.save(profile.withScopes(scopes));
        versions.increment(profile.userId());
    }

    public void replace(AuthorizationProfile replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        var existing = profiles.require(replacement.profileId());
        if (existing.userId() != replacement.userId()) {
            throw new IllegalArgumentException("authorization profile ownership cannot be changed");
        }
        profiles.save(replacement);
        versions.increment(replacement.userId());
    }

    public List<AuthorizationProfile> availableFor(IamPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        var now = clock.instant();
        return profiles.findByUserId(principal.userId()).stream()
                .filter(AuthorizationProfile::enabled)
                .filter(profile -> !profile.revoked())
                .filter(profile -> profile.clientTypes().contains(principal.clientType()))
                .filter(profile -> profile.validFrom() == null || !profile.validFrom().isAfter(now))
                .filter(profile -> profile.validUntil() == null || profile.validUntil().isAfter(now))
                .toList();
    }
}
