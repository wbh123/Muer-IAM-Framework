package io.github.iamstarter.web;

import io.github.iamstarter.audit.AuditEvent;
import io.github.iamstarter.audit.AuditEventFilter;
import io.github.iamstarter.audit.AuditEventPage;
import io.github.iamstarter.audit.AuditQueryRepository;
import io.github.iamstarter.audit.AuditSubjectLink;
import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.web.api.ManagementAuditApi;
import io.github.iamstarter.web.dto.AuditEventListResponse;
import io.github.iamstarter.web.dto.AuditEventResponse;
import io.github.iamstarter.web.dto.AuditSubjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static io.github.iamstarter.web.WebSecurity.allowedRead;
import static io.github.iamstarter.web.WebSecurity.currentPrincipal;

@RestController
public class IamManagementAuditController implements ManagementAuditApi {
    private static final String AUDIT_READ = "iam.admin.audit.read";

    private final AuthorizationEngine authorization;
    private final AuditQueryRepository audits;

    public IamManagementAuditController(AuthorizationEngine authorization, AuditQueryRepository audits) {
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.audits = Objects.requireNonNull(audits, "audits must not be null");
    }

    @Override
    public ResponseEntity<AuditEventListResponse> listAuditEvents(Long userId, String identityId,
                                                                  String eventType, String resourceType,
                                                                  String resourceId, String sessionId,
                                                                  Date from, Date to, Long after, Integer limit) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, AUDIT_READ, "IAM_AUDIT_COLLECTION", "audit-events")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int pageSize = limit == null ? 50 : limit;
        var filter = new AuditEventFilter(userId, identityId, sessionId, eventType, resourceType,
                resourceId, toInstant(from), toInstant(to));
        AuditEventPage page = audits.findEvents(filter, after == null ? 0L : after, pageSize);
        var items = page.events().stream().map(IamManagementAuditController::toResponse).toList();
        Long next = items.size() < pageSize ? null : page.lastLogId();
        return ResponseEntity.ok(new AuditEventListResponse(items).nextAfterAuditLogId(next));
    }

    @Override
    public ResponseEntity<AuditEventResponse> getAuditEvent(String eventId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, AUDIT_READ, "IAM_AUDIT_LOG", eventId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return audits.findById(eventId)
                .map(IamManagementAuditController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static AuditEventResponse toResponse(AuditEvent event) {
        var subjects = event.subjects().stream()
                .map(IamManagementAuditController::toSubject)
                .toList();
        return new AuditEventResponse(event.eventId(), Date.from(event.occurredAt()), event.actor(),
                event.action(), event.resourceType(), event.resourceId(), event.result(),
                event.requestId(), event.before(), event.after(), event.metadata(), subjects);
    }

    private static AuditSubjectResponse toSubject(AuditSubjectLink subject) {
        return new AuditSubjectResponse(subject.subjectType(), subject.subjectId(),
                subject.relation().name());
    }

    private static Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }
}
