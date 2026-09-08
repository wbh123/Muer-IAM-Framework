package cloud.muer.authorization;

import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.ScopeAccess;
import cloud.muer.core.port.IamUserRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit, idempotent bootstrap for the first administrator of an existing
 * host user projection. It never creates authentication credentials or HTTP endpoints.
 */
public final class MuerAdministrationBootstrapService {
    public static final String TEMPLATE_KEY = "muer-administrator";
    public static final String PROFILE_NAME = "muer-administrator";
    private static final String TEMPLATE_NAME = "Muer Administrator";
    private static final String TEMPLATE_DESCRIPTION = "Muer framework administration permissions";
    private static final List<ResourceScope> ADMINISTRATION_SCOPES = List.of(
            new ResourceScope("IAM_ADMIN", "*", ScopeAccess.READ),
            new ResourceScope("IAM_ADMIN", "*", ScopeAccess.WRITE));

    private final IamUserRepository users;
    private final PermissionTemplateQueryRepository queries;
    private final PermissionTemplateLifecycleService templates;
    private final AuthorizationProfileRepository profiles;
    private final AuthorizationProfileService profileService;

    public MuerAdministrationBootstrapService(IamUserRepository users, PermissionTemplateQueryRepository queries,
                                              PermissionTemplateLifecycleService templates,
                                              AuthorizationProfileRepository profiles,
                                              AuthorizationProfileService profileService) {
        this.users = Objects.requireNonNull(users); this.queries = Objects.requireNonNull(queries);
        this.templates = Objects.requireNonNull(templates); this.profiles = Objects.requireNonNull(profiles);
        this.profileService = Objects.requireNonNull(profileService);
    }

    public AdministrationBootstrapResult bootstrapFirstAdministrator(AdministrationBootstrapRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        users.require(request.userId());
        Set<String> permissions = registeredAdministrationPermissions();
        var template = findTemplate();
        boolean created = template == null;
        if (created) template = templates.createTemplate(TEMPLATE_KEY, TEMPLATE_NAME, TEMPLATE_DESCRIPTION);
        else validateTemplate(template);
        var version = queries.findVersionsByTemplate(template.templateId()).stream().findFirst().orElse(null);
        if (version == null) version = templates.createDraftVersion(template.templateId(), permissions);
        if (version.status() == TemplateVersionStatus.DRAFT) version = templates.publish(version.versionId());
        if (version.status() != TemplateVersionStatus.PUBLISHED || !version.permissions().equals(permissions)) {
            throw new IllegalStateException("existing administrator template version conflicts with registered permissions");
        }
        var existing = profiles.findByUserId(request.userId()).stream()
                .filter(profile -> PROFILE_NAME.equals(profile.profileName())).findFirst().orElse(null);
        if (existing != null) {
            if (existing.templateVersionId() != version.versionId() || !existing.clientTypes().equals(request.clientTypes())) {
                throw new IllegalStateException("existing administrator profile conflicts with bootstrap request");
            }
            if (existing.scopes().isEmpty()) {
                profileService.replaceScopes(existing.profileId(), ADMINISTRATION_SCOPES);
            } else if (!Set.copyOf(existing.scopes()).equals(Set.copyOf(ADMINISTRATION_SCOPES))) {
                throw new IllegalStateException("existing administrator profile conflicts with management root scopes");
            }
            return new AdministrationBootstrapResult(template.templateId(), version.versionId(), existing.profileId(), created);
        }
        var profile = profileService.create(new AuthorizationProfile(1L, request.userId(), PROFILE_NAME,
                version.versionId(), request.clientTypes(), true, false, null, null, ADMINISTRATION_SCOPES));
        return new AdministrationBootstrapResult(template.templateId(), version.versionId(), profile.profileId(), true);
    }

    private PermissionTemplate findTemplate() {
        long after = 0;
        while (true) {
            var page = queries.listTemplates(after, 100);
            var found = page.stream().filter(template -> TEMPLATE_KEY.equals(template.templateKey())).findFirst();
            if (found.isPresent()) return found.get();
            if (page.size() < 100) return null;
            after = page.getLast().templateId();
        }
    }

    private Set<String> registeredAdministrationPermissions() {
        var result = new LinkedHashSet<String>(); long after = 0;
        while (true) {
            var page = queries.listPermissions(null, "iam", after, 100);
            page.items().stream().map(PermissionSummary::permissionCode)
                    .filter(code -> code.startsWith("iam.admin.")).forEach(result::add);
            if (page.items().size() < 100) break;
            after = page.lastPermissionId();
        }
        if (result.isEmpty()) throw new IllegalStateException("no registered iam.admin.* permissions available for bootstrap");
        return Set.copyOf(result);
    }

    private static void validateTemplate(PermissionTemplate template) {
        if (!TEMPLATE_NAME.equals(template.name()) || !TEMPLATE_DESCRIPTION.equals(template.description()) || !template.enabled()) {
            throw new IllegalStateException("existing administrator template conflicts with bootstrap definition");
        }
    }
}
