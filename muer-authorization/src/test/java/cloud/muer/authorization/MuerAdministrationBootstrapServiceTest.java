package cloud.muer.authorization;

import cloud.muer.core.model.IamUser;
import cloud.muer.core.port.IamUserRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MuerAdministrationBootstrapServiceTest {
    @Test
    void first_bootstrap_publishes_registered_admin_permissions_and_is_idempotent() {
        var fixture = new Fixture();
        var first = fixture.service.bootstrapFirstAdministrator(new AdministrationBootstrapRequest(7L, Set.of("WEB")));
        var second = fixture.service.bootstrapFirstAdministrator(new AdministrationBootstrapRequest(7L, Set.of("WEB")));

        assertEquals(first.templateId(), second.templateId());
        assertEquals(first.templateVersionId(), second.templateVersionId());
        assertEquals(first.profileId(), second.profileId());
        assertEquals(1, fixture.templates.values.size());
        assertEquals(1, fixture.profiles.values.size());
        assertEquals(TemplateVersionStatus.PUBLISHED, fixture.versions.require(first.templateVersionId()).status());
        assertEquals(Set.of("iam.admin.user.read", "iam.admin.template.write"),
                fixture.versions.require(first.templateVersionId()).permissions());
    }

    @Test
    void conflicting_administrator_template_fails_fast() {
        var fixture = new Fixture();
        fixture.templates.values.add(new PermissionTemplate(9L, MuerAdministrationBootstrapService.TEMPLATE_KEY,
                "Different", "wrong", true));

        assertThrows(IllegalStateException.class, () -> fixture.service.bootstrapFirstAdministrator(
                new AdministrationBootstrapRequest(7L, Set.of("WEB"))));
    }

    @Test
    void bootstrapping_a_second_user_reuses_template_without_changing_first_user() {
        var fixture = new Fixture();
        var first = fixture.service.bootstrapFirstAdministrator(new AdministrationBootstrapRequest(7L, Set.of("WEB")));
        var second = fixture.service.bootstrapFirstAdministrator(new AdministrationBootstrapRequest(8L, Set.of("WEB")));

        assertEquals(first.templateId(), second.templateId());
        assertEquals(first.templateVersionId(), second.templateVersionId());
        assertEquals(2, fixture.profiles.values.size());
        assertEquals(first.profileId(), fixture.profiles.findByUserId(7L).getFirst().profileId());
        assertEquals(second.profileId(), fixture.profiles.findByUserId(8L).getFirst().profileId());
    }

    private static final class Fixture {
        final Versions versions = new Versions(); final Templates templates = new Templates(versions); final Profiles profiles = new Profiles();
        final MuerAdministrationBootstrapService service;
        Fixture() {
            var lifecycle = new PermissionTemplateLifecycleService(templates, versions);
            var profileService = new AuthorizationProfileService(profiles, new AuthorizationVersionService(new VersionNumbers()), versions, null);
            service = new MuerAdministrationBootstrapService(new Users(), new Queries(templates, versions), lifecycle, profiles, profileService);
        }
    }
    private static final class Users implements IamUserRepository {
        public Optional<IamUser> findById(long id) { return id == 7 || id == 8 ? Optional.of(new IamUser(id, "admin-" + id, "HOST", true, 0)) : Optional.empty(); }
        public List<IamUser> findPage(long after, int limit) { return List.of(); } public void save(IamUser user) { }
    }
    private static final class Templates implements PermissionTemplateCommandRepository {
        long nextTemplate = 1, nextVersion = 1; final List<PermissionTemplate> values = new ArrayList<>(); final Versions versions;
        Templates(Versions versions) { this.versions = versions; }
        public PermissionTemplate createTemplate(String key, String name, String description, boolean enabled) { var value = new PermissionTemplate(nextTemplate++, key, name, description, enabled); values.add(value); return value; }
        public PermissionTemplateVersion createNextDraftVersion(long templateId, Set<String> permissions) { var value = new PermissionTemplateVersion(nextVersion++, templateId, 1, TemplateVersionStatus.DRAFT, permissions); versions.save(value); return value; }
    }
    private static final class Versions implements PermissionTemplateVersionRepository {
        final Map<Long, PermissionTemplateVersion> values = new HashMap<>();
        public PermissionTemplateVersion require(long id) { return values.get(id); }
        public void save(PermissionTemplateVersion version) { values.put(version.versionId(), version); }
    }
    private static final class Profiles implements AuthorizationProfileRepository {
        long next = 1; final Map<Long, AuthorizationProfile> values = new HashMap<>();
        public AuthorizationProfile require(long id) { return values.get(id); }
        public List<AuthorizationProfile> findByUserId(long id) { return values.values().stream().filter(p -> p.userId() == id).toList(); }
        public void save(AuthorizationProfile profile) { values.put(profile.profileId(), profile); }
        public AuthorizationProfile create(AuthorizationProfile profile) { var created = new AuthorizationProfile(next++, profile.userId(), profile.profileName(), profile.templateVersionId(), profile.clientTypes(), profile.enabled(), profile.revoked(), null, null, List.of()); values.put(created.profileId(), created); return created; }
    }
    private static final class VersionNumbers implements AuthorizationVersionRepository { public long currentVersion(long id) { return 0; } public long increment(long id) { return 1; } }
    private record Queries(Templates templates, Versions versions) implements PermissionTemplateQueryRepository {
        public Optional<PermissionTemplate> findTemplate(long id) { return templates.values.stream().filter(t -> t.templateId() == id).findFirst(); }
        public List<PermissionTemplate> listTemplates(long after, int limit) { return templates.values.stream().filter(t -> t.templateId() > after).toList(); }
        public List<PermissionTemplateVersion> findVersionsByTemplate(long id) { return versions.values.values().stream().filter(v -> v.templateId() == id).toList(); }
        public Optional<PermissionTemplateVersion> findVersionById(long id) { return Optional.ofNullable(versions.values.get(id)); }
        public PermissionSummaryPage listPermissions(String keyword, String domain, long after, int limit) { return new PermissionSummaryPage(List.of(new PermissionSummary("iam.admin.user.read", "Users", null, true, 0), new PermissionSummary("iam.admin.template.write", "Templates", null, true, 0)), 2); }
    }
}
