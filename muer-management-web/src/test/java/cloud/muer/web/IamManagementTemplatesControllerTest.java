package cloud.muer.web;

import cloud.muer.authorization.AuthorizationDecision;
import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.authorization.PermissionSummary;
import cloud.muer.authorization.PermissionSummaryPage;
import cloud.muer.authorization.PermissionTemplate;
import cloud.muer.authorization.PermissionTemplateQueryRepository;
import cloud.muer.authorization.PermissionTemplateVersion;
import cloud.muer.authorization.TemplateVersionStatus;
import cloud.muer.core.model.IamPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamManagementTemplatesControllerTest {
    @AfterEach
    void clear_security_context() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void template_listing_returns_template_summaries_with_latest_version() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();

        mvc.perform(get("/iam/admin/templates").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].templateKey").value("document"))
                .andExpect(jsonPath("$.items[0].latestVersionNumber").value(2))
                .andExpect(jsonPath("$.items[0].latestVersionStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.nextAfterTemplateId").value(1));
    }

    @Test
    void template_queries_require_the_template_read_permission() throws Exception {
        authenticate();
        var fixture = fixture(false);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/templates")).andExpect(status().isForbidden());
        mvc.perform(get("/iam/admin/templates/1/versions")).andExpect(status().isForbidden());
        mvc.perform(get("/iam/admin/template-versions/11")).andExpect(status().isForbidden());
    }

    @Test
    void unknown_template_and_version_yield_not_found() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/templates/999")).andExpect(status().isNotFound());
        mvc.perform(get("/iam/admin/template-versions/999")).andExpect(status().isNotFound());
    }

    @Test
    void version_queries_return_versions_newest_first_with_permission_codes() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/templates/1/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].permissions[0]").value("document:read"));
    }

    @Test
    void permission_explorer_filters_and_returns_in_use_counts() throws Exception {
        authenticate();
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/permissions").param("keyword", "document"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].permissionCode").value("document:read"))
                .andExpect(jsonPath("$.items[0].inUseCount").value(2))
                .andExpect(jsonPath("$.items[0].enabled").value(true));
    }

    @Test
    void unauthenticated_management_queries_are_rejected() throws Exception {
        var fixture = fixture(true);
        var mvc = MockMvcBuilders.standaloneSetup(fixture.controller()).build();
        mvc.perform(get("/iam/admin/templates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/iam/admin/permissions")).andExpect(status().isUnauthorized());
    }

    private static void authenticate() {
        var principal = new IamPrincipal(99L, "administrator", "SECURITY", 40L, 10L, "WEB", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static Fixture fixture(boolean allowed) {
        AuthorizationEngine engine = (principal, request) ->
                new AuthorizationDecision(allowed, allowed ? "ALLOW" : "DENY", List.of());
        return new Fixture(new IamManagementTemplatesController(engine, new FakeTemplates()));
    }

    private record Fixture(IamManagementTemplatesController controller) {
    }

    private static final class FakeTemplates implements PermissionTemplateQueryRepository {
        private final List<PermissionTemplate> templates = new ArrayList<>(List.of(
                new PermissionTemplate(1L, "document", "Document", "Document permissions", true)));
        private final List<PermissionTemplateVersion> versions = new ArrayList<>(List.of(
                new PermissionTemplateVersion(12L, 1L, 2, TemplateVersionStatus.PUBLISHED,
                        Set.of("document:read", "document:update")),
                new PermissionTemplateVersion(11L, 1L, 1, TemplateVersionStatus.DRAFT,
                        Set.of("document:read"))));
        private final List<PermissionSummary> permissions = new ArrayList<>(List.of(
                new PermissionSummary("document:read", "Read document", "Read documents", true, 2L),
                new PermissionSummary("document:update", "Update document", null, true, 1L)));

        @Override
        public Optional<PermissionTemplate> findTemplate(long templateId) {
            return templates.stream().filter(value -> value.templateId() == templateId).findFirst();
        }

        @Override
        public List<PermissionTemplate> listTemplates(long afterTemplateId, int limit) {
            return templates.stream().filter(value -> value.templateId() > afterTemplateId)
                    .limit(limit).toList();
        }

        @Override
        public List<PermissionTemplateVersion> findVersionsByTemplate(long templateId) {
            return versions.stream().filter(value -> value.templateId() == templateId).toList();
        }

        @Override
        public Optional<PermissionTemplateVersion> findVersionById(long versionId) {
            return versions.stream().filter(value -> value.versionId() == versionId).findFirst();
        }

        @Override
        public PermissionSummaryPage listPermissions(String keyword, String domain,
                                                     long afterPermissionId, int limit) {
            var filtered = permissions.stream()
                    .filter(value -> keyword == null || value.permissionCode().contains(keyword))
                    .limit(limit)
                    .toList();
            long last = filtered.isEmpty() ? afterPermissionId : 2L;
            return new PermissionSummaryPage(filtered, last);
        }
    }
}
