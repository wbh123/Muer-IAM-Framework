package cloud.muer.web;

import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.core.port.OverviewRepository;
import cloud.muer.web.api.ManagementOverviewApi;
import cloud.muer.web.dto.OverviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static cloud.muer.web.WebSecurity.allowedRead;
import static cloud.muer.web.WebSecurity.currentPrincipal;

@RestController
public class IamManagementOverviewController implements ManagementOverviewApi {
    private static final String OVERVIEW_READ = "iam.admin.overview.read";

    private final AuthorizationEngine authorization;
    private final OverviewRepository overview;

    public IamManagementOverviewController(AuthorizationEngine authorization, OverviewRepository overview) {
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.overview = Objects.requireNonNull(overview, "overview must not be null");
    }

    @Override
    public ResponseEntity<OverviewResponse> getAdminOverview() {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, OVERVIEW_READ, "IAM_OVERVIEW", "overview")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var metrics = overview.load();
        return ResponseEntity.ok(new OverviewResponse(metrics.totalUsers(), metrics.enabledUsers(),
                metrics.activeSessions(), metrics.activeProfiles(), metrics.templateVersions(),
                metrics.auditEventsToday()));
    }
}
