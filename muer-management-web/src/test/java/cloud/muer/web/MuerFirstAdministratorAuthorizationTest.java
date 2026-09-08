package cloud.muer.web;

import cloud.muer.authorization.AdministrationBootstrapRequest;
import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.authorization.AuthorizationProfileRepository;
import cloud.muer.authorization.AuthorizationProfileService;
import cloud.muer.authorization.AuthorizationVersionRepository;
import cloud.muer.authorization.AuthorizationVersionService;
import cloud.muer.authorization.DefaultAuthorizationEngine;
import cloud.muer.authorization.MuerAdministrationBootstrapService;
import cloud.muer.authorization.PermissionSummary;
import cloud.muer.authorization.PermissionSummaryPage;
import cloud.muer.authorization.PermissionTemplate;
import cloud.muer.authorization.PermissionTemplateCommandRepository;
import cloud.muer.authorization.PermissionTemplateLifecycleService;
import cloud.muer.authorization.PermissionTemplateQueryRepository;
import cloud.muer.authorization.PermissionTemplateVersion;
import cloud.muer.authorization.PermissionTemplateVersionRepository;
import cloud.muer.authorization.TemplateVersionStatus;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.model.IamUser;
import cloud.muer.core.model.ScopeAccess;
import cloud.muer.core.port.IamUserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuerFirstAdministratorAuthorizationTest {
    @Test
    void bootstrapped_administrator_is_allowed_for_the_real_template_collection_resource() {
        var fixture = new Fixture();
        var result = fixture.bootstrap.bootstrapFirstAdministrator(
                new AdministrationBootstrapRequest(7L, Set.of("WEB")));
        var principal = new IamPrincipal(7L, "host-admin", "HOST", result.profileId(),
                result.templateVersionId(), "WEB", 1L);

        assertTrue(WebSecurity.allowedRead(fixture.engine(), principal, "iam.admin.template.read",
                "IAM_PERMISSION_TEMPLATE_COLLECTION", "templates"));
    }

    @Test
    void profile_without_management_root_scope_is_denied_for_the_same_resource() {
        var fixture = new Fixture();
        fixture.bootstrap.bootstrapFirstAdministrator(new AdministrationBootstrapRequest(7L, Set.of("WEB")));
        fixture.profiles.save(new AuthorizationProfile(99L, 8L, "ordinary", 1L, Set.of("WEB"),
                true, false, null, null, List.of()));
        var principal = new IamPrincipal(8L, "ordinary", "HOST", 99L, 1L, "WEB", 1L);

        assertFalse(WebSecurity.allowedRead(fixture.engine(), principal, "iam.admin.template.read",
                "IAM_PERMISSION_TEMPLATE_COLLECTION", "templates"));
    }

    private static final class Fixture {
        final Versions versions = new Versions();
        final Templates templates = new Templates(versions);
        final Profiles profiles = new Profiles();
        final MuerAdministrationBootstrapService bootstrap;

        Fixture() {
            var lifecycle = new PermissionTemplateLifecycleService(templates, versions);
            var profileService = new AuthorizationProfileService(profiles,
                    new AuthorizationVersionService(new VersionNumbers()), versions, null);
            bootstrap = new MuerAdministrationBootstrapService(new Users(), new Queries(templates, versions),
                    lifecycle, profiles, profileService);
        }

        DefaultAuthorizationEngine engine() {
            return new DefaultAuthorizationEngine(
                    (resource, scope) -> "IAM_ADMIN".equals(scope.scopeType())
                            && "*".equals(scope.scopeRefId())
                            && Set.of("IAM_AUDIT_COLLECTION", "IAM_AUDIT_LOG", "IAM_AUTHORIZATION_PROFILE",
                            "IAM_AUTHORIZATION_PROFILE_COLLECTION", "IAM_IDENTITY", "IAM_OVERVIEW",
                            "IAM_PERMISSION_COLLECTION", "IAM_PERMISSION_TEMPLATE",
                            "IAM_PERMISSION_TEMPLATE_COLLECTION", "IAM_PERMISSION_TEMPLATE_VERSION",
                            "IAM_SESSION", "IAM_SESSION_COLLECTION", "IAM_USER", "IAM_USER_COLLECTION")
                            .contains(resource.resourceType()),
                    principal -> versions.require(profiles.require(principal.activeProfileId())
                            .templateVersionId()).permissions(),
                    principal -> profiles.require(principal.activeProfileId()).scopes(),
                    principal -> profiles.require(principal.activeProfileId()), Clock.systemUTC());
        }
    }

    private static final class Users implements IamUserRepository {
        public Optional<IamUser> findById(long id) {
            return id == 7 || id == 8
                    ? Optional.of(new IamUser(id, "user-" + id, "HOST", true, 1L))
                    : Optional.empty();
        }
        public List<IamUser> findPage(long after, int limit) { return List.of(); }
        public void save(IamUser user) { }
    }

    private static final class Templates implements PermissionTemplateCommandRepository {
        long nextTemplate = 1L;
        long nextVersion = 1L;
        final List<PermissionTemplate> values = new ArrayList<>();
        final Versions versions;

        Templates(Versions versions) { this.versions = versions; }
        public PermissionTemplate createTemplate(String key, String name, String description, boolean enabled) {
            var value = new PermissionTemplate(nextTemplate++, key, name, description, enabled);
            values.add(value);
            return value;
        }
        public PermissionTemplateVersion createNextDraftVersion(long templateId, Set<String> permissions) {
            var value = new PermissionTemplateVersion(nextVersion++, templateId, 1,
                    TemplateVersionStatus.DRAFT, permissions);
            versions.save(value);
            return value;
        }
    }

    private static final class Versions implements PermissionTemplateVersionRepository {
        final Map<Long, PermissionTemplateVersion> values = new HashMap<>();
        public PermissionTemplateVersion require(long id) { return values.get(id); }
        public void save(PermissionTemplateVersion version) { values.put(version.versionId(), version); }
    }

    private static final class Profiles implements AuthorizationProfileRepository {
        long next = 1L;
        final Map<Long, AuthorizationProfile> values = new HashMap<>();
        public AuthorizationProfile require(long id) { return values.get(id); }
        public List<AuthorizationProfile> findByUserId(long id) {
            return values.values().stream().filter(profile -> profile.userId() == id).toList();
        }
        public void save(AuthorizationProfile profile) { values.put(profile.profileId(), profile); }
        public AuthorizationProfile create(AuthorizationProfile profile) {
            var created = new AuthorizationProfile(next++, profile.userId(), profile.profileName(),
                    profile.templateVersionId(), profile.clientTypes(), profile.enabled(), profile.revoked(),
                    profile.validFrom(), profile.validUntil(), profile.scopes());
            values.put(created.profileId(), created);
            return created;
        }
    }

    private static final class VersionNumbers implements AuthorizationVersionRepository {
        public long currentVersion(long id) { return 1L; }
        public long increment(long id) { return 2L; }
    }

    private record Queries(Templates templates, Versions versions) implements PermissionTemplateQueryRepository {
        public Optional<PermissionTemplate> findTemplate(long id) {
            return templates.values.stream().filter(template -> template.templateId() == id).findFirst();
        }
        public List<PermissionTemplate> listTemplates(long after, int limit) {
            return templates.values.stream().filter(template -> template.templateId() > after).limit(limit).toList();
        }
        public List<PermissionTemplateVersion> findVersionsByTemplate(long id) {
            return versions.values.values().stream().filter(version -> version.templateId() == id).toList();
        }
        public Optional<PermissionTemplateVersion> findVersionById(long id) {
            return Optional.ofNullable(versions.values.get(id));
        }
        public PermissionSummaryPage listPermissions(String keyword, String domain, long after, int limit) {
            var item = new PermissionSummary("iam.admin.template.read", "Read templates", null, true, 0L);
            return new PermissionSummaryPage(after == 0 ? List.of(item) : List.of(), 1L);
        }
    }
}
