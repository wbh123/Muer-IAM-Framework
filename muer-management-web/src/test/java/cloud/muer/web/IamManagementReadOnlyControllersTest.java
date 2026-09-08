package cloud.muer.web;

import cloud.muer.audit.AuditEvent;
import cloud.muer.audit.AuditEventFilter;
import cloud.muer.audit.AuditEventPage;
import cloud.muer.audit.AuditQueryRepository;
import cloud.muer.audit.AuditSubjectLink;
import cloud.muer.audit.AuditSubjectRelation;
import cloud.muer.authorization.AuthorizationDecision;
import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.authorization.AuthorizationProfileRepository;
import cloud.muer.authorization.PermissionTemplateVersion;
import cloud.muer.authorization.PermissionTemplateVersionRepository;
import cloud.muer.authorization.TemplateVersionStatus;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.model.OverviewMetrics;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.model.ScopeAccess;
import cloud.muer.core.port.OverviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamManagementReadOnlyControllersTest {
    private static final IamPrincipal ADMIN =
            new IamPrincipal(99L, "administrator", "SECURITY", 40L, 10L, "WEB", 2L);

    @AfterEach
    void clear_security_context() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void audit_events_are_read_only_and_filters_are_forwarded() throws Exception {
        authenticate();
        var audits = new FakeAudits();
        var engine = alwaysAllowed();
        var mvc = MockMvcBuilders.standaloneSetup(
                new IamManagementAuditController(engine, audits)).build();

        mvc.perform(get("/iam/admin/audit-events").param("eventType", "user.login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value("audit-1"))
                .andExpect(jsonPath("$.items[0].actor").value("user:7"))
                .andExpect(jsonPath("$.items[0].metadata.reason").value("rotation"))
                .andExpect(jsonPath("$.items[0].subjects[0].subjectType").value("USER"));

        mvc.perform(get("/iam/admin/audit-events/audit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("user.login"));
        mvc.perform(get("/iam/admin/audit-events/missing")).andExpect(status().isNotFound());
    }

    @Test
    void audit_query_requires_the_audit_read_permission() throws Exception {
        authenticate();
        var mvc = MockMvcBuilders.standaloneSetup(
                new IamManagementAuditController(alwaysDenied(), new FakeAudits())).build();
        mvc.perform(get("/iam/admin/audit-events")).andExpect(status().isForbidden());
    }

    @Test
    void overview_returns_lightweight_operational_counts() throws Exception {
        authenticate();
        var mvc = MockMvcBuilders.standaloneSetup(
                new IamManagementOverviewController(alwaysAllowed(), () ->
                        new OverviewMetrics(12, 10, 3, 4, 9, 25))).build();
        mvc.perform(get("/iam/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(12))
                .andExpect(jsonPath("$.activeSessions").value(3))
                .andExpect(jsonPath("$.auditEventsToday").value(25));
    }

    @Test
    void capabilities_expose_only_the_current_principals_permissions_and_scopes() throws Exception {
        authenticate();
        var profile = new AuthorizationProfile(40L, 99L, "Console Admin", 10L, Set.of("WEB"),
                true, false, null, null,
                List.of(new ResourceScope("IAM_ADMIN", "console", ScopeAccess.READ)));
        var template = new PermissionTemplateVersion(10L, 4L, 1, TemplateVersionStatus.PUBLISHED,
                Set.of("iam.admin.user.read", "iam.admin.session.read"));
        var mvc = MockMvcBuilders.standaloneSetup(new IamCapabilitiesController(
                repo(profile), versionRepo(template))).build();

        mvc.perform(get("/iam/auth/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal.userId").value(99))
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.scopes[0].scopeType").value("IAM_ADMIN"));
    }

    @Test
    void capabilities_require_an_authenticated_principal() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new IamCapabilitiesController(
                repo(null), versionRepo(null))).build();
        mvc.perform(get("/iam/auth/capabilities")).andExpect(status().isUnauthorized());
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ADMIN, null, List.of()));
    }

    private static AuthorizationEngine alwaysAllowed() {
        return (principal, request) -> new AuthorizationDecision(true, "ALLOW", List.of());
    }

    private static AuthorizationEngine alwaysDenied() {
        return (principal, request) -> new AuthorizationDecision(false, "DENY", List.of());
    }

    private static AuthorizationProfileRepository repo(AuthorizationProfile profile) {
        return new AuthorizationProfileRepository() {
            @Override
            public AuthorizationProfile require(long profileId) {
                if (profile == null || profile.profileId() != profileId) {
                    throw new IllegalArgumentException("missing");
                }
                return profile;
            }

            @Override
            public List<AuthorizationProfile> findByUserId(long userId) {
                return profile == null ? List.of() : List.of(profile);
            }

            @Override
            public void save(AuthorizationProfile value) {
            }
        };
    }

    private static PermissionTemplateVersionRepository versionRepo(PermissionTemplateVersion version) {
        return new PermissionTemplateVersionRepository() {
            @Override
            public PermissionTemplateVersion require(long versionId) {
                if (version == null || version.versionId() != versionId) {
                    throw new IllegalArgumentException("missing");
                }
                return version;
            }

            @Override
            public void save(PermissionTemplateVersion value) {
            }
        };
    }

    private static final class FakeAudits implements AuditQueryRepository {
        private final List<AuditEvent> events = List.of(new AuditEvent(
                "audit-1", Instant.parse("2026-08-30T00:00:00Z"), "user:7", "user.login",
                "SESSION", "session-1", "SUCCESS", "request-1",
                Map.of(), Map.of(), Map.of("reason", "rotation"),
                List.of(new AuditSubjectLink("USER", "7", AuditSubjectRelation.PRIMARY))));

        @Override
        public Optional<AuditEvent> findById(String eventId) {
            return events.stream().filter(event -> event.eventId().equals(eventId)).findFirst();
        }

        @Override
        public AuditEventPage findEvents(AuditEventFilter filter, long maxLogId, int limit) {
            var filtered = events.stream().filter(event ->
                    filter.eventType() == null || filter.eventType().equals(event.action())).toList();
            return new AuditEventPage(filtered, filtered.isEmpty() ? maxLogId : 1L);
        }
    }
}
