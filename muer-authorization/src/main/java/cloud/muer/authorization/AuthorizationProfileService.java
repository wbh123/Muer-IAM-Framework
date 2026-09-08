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
    private final PermissionTemplateVersionRepository templateVersions;

    public AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions) {
        this(profiles, versions, Clock.systemUTC(), null, null);
    }

    public AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions,
                                       Clock clock) {
        this(profiles, versions, clock, null, null);
    }

    public AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions,
                                       AuthorizationScopeMutation scopeMutation) {
        this(profiles, versions, Clock.systemUTC(), Objects.requireNonNull(scopeMutation), null);
    }

    /** Enables the invariant that a profile may only bind to a published template version. */
    public AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions,
                                       PermissionTemplateVersionRepository templateVersions,
                                       AuthorizationScopeMutation scopeMutation) {
        this(profiles, versions, Clock.systemUTC(), scopeMutation, templateVersions);
    }

    private AuthorizationProfileService(AuthorizationProfileRepository profiles, AuthorizationVersionService versions,
                                        Clock clock, AuthorizationScopeMutation scopeMutation,
                                        PermissionTemplateVersionRepository templateVersions) {
        this.profiles = Objects.requireNonNull(profiles);
        this.versions = Objects.requireNonNull(versions);
        this.clock = Objects.requireNonNull(clock);
        this.scopeMutation = scopeMutation;
        this.templateVersions = templateVersions;
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
        requirePublishedTemplateVersion(replacement.templateVersionId());
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

    private void requirePublishedTemplateVersion(long templateVersionId) {
        if (templateVersions != null
                && templateVersions.require(templateVersionId).status() != TemplateVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("authorization profiles require a published template version");
        }
    }
}
