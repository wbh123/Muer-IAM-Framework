package cloud.muer.authorization;

import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.ScopeAccess;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationProfileServiceTest {
    @Test
    void changing_profile_scope_increments_version_and_invalidates_old_principal() {
        var versions = new InMemoryVersions(Map.of(7L, 4L));
        var profiles = new InMemoryProfiles(new AuthorizationProfile(
                19L, 7L, "Approver", 3L, Set.of("WEB"), true, false, null, null,
                List.of(new ResourceScope("DEPARTMENT", "7", ScopeAccess.READ))));
        var service = new AuthorizationProfileService(profiles, new AuthorizationVersionService(versions));

        service.replaceScopes(19L, List.of(new ResourceScope("DEPARTMENT", "12", ScopeAccess.WRITE)));

        assertEquals(5L, versions.currentVersion(7L));
        assertFalse(new AuthorizationVersionService(versions).isCurrent(
                new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 4L)));
    }

    @Test
    void published_template_version_cannot_be_mutated() {
        var templates = new PermissionTemplateService(new InMemoryTemplates(
                new PermissionTemplateVersion(9L, 2L, 1, TemplateVersionStatus.PUBLISHED, Set.of("order.read"))));

        assertThrows(IllegalStateException.class, () -> templates.addPermission(9L, "order.refund"));
    }

    @Test
    void available_profiles_are_owned_active_current_and_support_the_principals_client() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        var available = profile(19L, 7L, Set.of("WEB"), true, false,
                now.minusSeconds(60), now.plusSeconds(60));
        var profiles = new InMemoryProfiles(
                available,
                profile(20L, 7L, Set.of("MOBILE"), true, false, null, null),
                profile(21L, 7L, Set.of("WEB"), false, false, null, null),
                profile(22L, 7L, Set.of("WEB"), true, true, null, null),
                profile(23L, 7L, Set.of("WEB"), true, false, null, now),
                profile(24L, 8L, Set.of("WEB"), true, false, null, null));
        var service = new AuthorizationProfileService(profiles,
                new AuthorizationVersionService(new InMemoryVersions(Map.of())),
                Clock.fixed(now, ZoneOffset.UTC));
        var principal = new IamPrincipal(7L, "id-7", "BUSINESS", 19L, 3L, "WEB", 4L);

        assertEquals(List.of(available), service.availableFor(principal));
    }

    @Test
    void replacing_a_profile_preserves_ownership_and_increments_authorization_version() {
        var original = profile(30L, 7L, Set.of("WEB"), true, false, null, null);
        var profiles = new InMemoryProfiles(original);
        var versions = new InMemoryVersions(Map.of(7L, 4L));
        var service = new AuthorizationProfileService(profiles, new AuthorizationVersionService(versions));
        var replacement = new AuthorizationProfile(30L, 7L, "Updated", 4L, Set.of("WEB"), true, false,
                null, null, List.of());

        service.replace(replacement);

        assertEquals(replacement, profiles.require(30L));
        assertEquals(5L, versions.currentVersion(7L));
        assertThrows(IllegalArgumentException.class, () -> service.replace(
                new AuthorizationProfile(30L, 8L, "Reassigned", 4L, Set.of("WEB"), true, false,
                        null, null, List.of())));
    }

    @Test
    void replacing_a_template_version_can_publish_a_draft_but_cannot_mutate_a_publication() {
        var repository = new InMemoryTemplates(
                new PermissionTemplateVersion(9L, 2L, 1, TemplateVersionStatus.DRAFT, Set.of("asset.read")));
        var templates = new PermissionTemplateService(repository);
        var published = new PermissionTemplateVersion(
                9L, 2L, 1, TemplateVersionStatus.PUBLISHED, Set.of("asset.read"));

        templates.replace(published);

        assertEquals(published, repository.require(9L));
        assertThrows(IllegalStateException.class, () -> templates.replace(
                new PermissionTemplateVersion(9L, 2L, 1, TemplateVersionStatus.PUBLISHED,
                        Set.of("asset.read", "asset.write"))));
    }

    private static AuthorizationProfile profile(long profileId, long userId, Set<String> clients,
                                                boolean enabled, boolean revoked,
                                                Instant validFrom, Instant validUntil) {
        return new AuthorizationProfile(profileId, userId, "profile-" + profileId, 3L, clients,
                enabled, revoked, validFrom, validUntil, List.of());
    }

    private static final class InMemoryVersions implements AuthorizationVersionRepository {
        private final Map<Long, Long> versions = new HashMap<>();
        private InMemoryVersions(Map<Long, Long> initial) { versions.putAll(initial); }
        public long currentVersion(long userId) { return versions.getOrDefault(userId, 0L); }
        public long increment(long userId) { return versions.merge(userId, 1L, Long::sum); }
    }

    private static final class InMemoryProfiles implements AuthorizationProfileRepository {
        private final List<AuthorizationProfile> profiles = new ArrayList<>();
        private InMemoryProfiles(AuthorizationProfile... profiles) { this.profiles.addAll(List.of(profiles)); }
        public AuthorizationProfile require(long profileId) {
            return profiles.stream().filter(profile -> profile.profileId() == profileId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("profile not found"));
        }
        public void save(AuthorizationProfile profile) {
            profiles.removeIf(existing -> existing.profileId() == profile.profileId());
            profiles.add(profile);
        }
        public List<AuthorizationProfile> findByUserId(long userId) {
            return profiles.stream().filter(profile -> profile.userId() == userId).toList();
        }
    }

    private static final class InMemoryTemplates implements PermissionTemplateVersionRepository {
        private PermissionTemplateVersion version;
        private InMemoryTemplates(PermissionTemplateVersion version) { this.version = version; }
        public PermissionTemplateVersion require(long versionId) { return version; }
        public void save(PermissionTemplateVersion version) { this.version = version; }
    }
}
