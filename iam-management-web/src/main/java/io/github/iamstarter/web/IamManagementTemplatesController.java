package io.github.iamstarter.web;

import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.PermissionSummaryPage;
import io.github.iamstarter.authorization.PermissionTemplate;
import io.github.iamstarter.authorization.PermissionTemplateQueryRepository;
import io.github.iamstarter.authorization.PermissionTemplateVersion;
import io.github.iamstarter.authorization.TemplateVersionStatus;
import io.github.iamstarter.web.api.ManagementPermissionsApi;
import io.github.iamstarter.web.api.ManagementTemplatesApi;
import io.github.iamstarter.web.dto.PermissionListResponse;
import io.github.iamstarter.web.dto.PermissionTemplateListResponse;
import io.github.iamstarter.web.dto.PermissionTemplateSummary;
import io.github.iamstarter.web.dto.PermissionTemplateVersionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.github.iamstarter.web.WebSecurity.allowedRead;
import static io.github.iamstarter.web.WebSecurity.currentPrincipal;

@RestController
public class IamManagementTemplatesController implements ManagementTemplatesApi, ManagementPermissionsApi {
    private static final String TEMPLATE_READ = "iam.admin.template.read";
    private static final String PERMISSION_READ = "iam.admin.permission.read";

    private final AuthorizationEngine authorization;
    private final PermissionTemplateQueryRepository templates;

    public IamManagementTemplatesController(AuthorizationEngine authorization,
                                            PermissionTemplateQueryRepository templates) {
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.templates = Objects.requireNonNull(templates, "templates must not be null");
    }

    @Override
    public ResponseEntity<PermissionTemplateListResponse> listPermissionTemplates(Long afterTemplateId,
                                                                                  Integer limit) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, TEMPLATE_READ,
                "IAM_PERMISSION_TEMPLATE_COLLECTION", "templates")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int pageSize = limit == null ? 50 : limit;
        var page = templates.listTemplates(afterTemplateId == null ? 0L : afterTemplateId, pageSize);
        var items = page.stream().map(template -> templateSummary(template, templates)).toList();
        Long next = items.size() < pageSize ? null : items.getLast().getTemplateId();
        return ResponseEntity.ok(new PermissionTemplateListResponse(items).nextAfterTemplateId(next));
    }

    @Override
    public ResponseEntity<PermissionTemplateSummary> getPermissionTemplate(Long templateId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, TEMPLATE_READ,
                "IAM_PERMISSION_TEMPLATE", templateId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return templates.findTemplate(templateId)
                .map(template -> ResponseEntity.ok(templateSummary(template, templates)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<PermissionTemplateVersionResponse>> listPermissionTemplateVersions(Long templateId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, TEMPLATE_READ,
                "IAM_PERMISSION_TEMPLATE", templateId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (templates.findTemplate(templateId).isEmpty()) return ResponseEntity.notFound().build();
        var versions = templates.findVersionsByTemplate(templateId).stream()
                .map(IamManagementTemplatesController::versionResponse)
                .toList();
        return ResponseEntity.ok(versions);
    }

    @Override
    public ResponseEntity<PermissionTemplateVersionResponse> getPermissionTemplateVersion(Long versionId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, TEMPLATE_READ,
                "IAM_PERMISSION_TEMPLATE_VERSION", versionId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return templates.findVersionById(versionId)
                .map(IamManagementTemplatesController::versionResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<PermissionListResponse> listPermissions(String keyword, String domain,
                                                                  Long afterId, Integer limit) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, PERMISSION_READ,
                "IAM_PERMISSION_COLLECTION", "permissions")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int pageSize = limit == null ? 50 : limit;
        PermissionSummaryPage page = templates.listPermissions(keyword, domain,
                afterId == null ? 0L : afterId, pageSize);
        var items = new ArrayList<io.github.iamstarter.web.dto.PermissionSummary>(page.items().size());
        for (var summary : page.items()) {
            items.add(new io.github.iamstarter.web.dto.PermissionSummary(
                    summary.permissionCode(), summary.displayName(), summary.description(),
                    summary.enabled(), summary.inUseCount()));
        }
        boolean exhausted = page.items().size() < pageSize;
        Long next = exhausted ? null : page.lastPermissionId();
        return ResponseEntity.ok(new PermissionListResponse(items).nextAfterPermissionId(next));
    }

    private static PermissionTemplateSummary templateSummary(PermissionTemplate template,
                                                             PermissionTemplateQueryRepository repository) {
        var summary = new PermissionTemplateSummary(template.templateId(), template.templateKey(),
                template.name(), template.description(), template.enabled());
        repository.findVersionsByTemplate(template.templateId()).stream().findFirst().ifPresent(latest -> summary
                .latestVersionId(latest.versionId())
                .latestVersionNumber(latest.versionNumber())
                .latestVersionStatus(latestVersionStatus(latest.status())));
        return summary;
    }

    private static PermissionTemplateVersionResponse versionResponse(PermissionTemplateVersion version) {
        return new PermissionTemplateVersionResponse(version.versionId(), version.templateId(),
                version.versionNumber(), versionStatus(version.status()),
                new java.util.TreeSet<>(version.permissions()));
    }

    private static PermissionTemplateVersionResponse.StatusEnum versionStatus(TemplateVersionStatus status) {
        return PermissionTemplateVersionResponse.StatusEnum.fromValue(status.name());
    }

    private static PermissionTemplateSummary.LatestVersionStatusEnum latestVersionStatus(
            TemplateVersionStatus status) {
        return PermissionTemplateSummary.LatestVersionStatusEnum.fromValue(status.name());
    }
}
