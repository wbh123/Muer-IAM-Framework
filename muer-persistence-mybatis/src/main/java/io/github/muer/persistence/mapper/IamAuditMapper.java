package io.github.muer.persistence.mapper;

import io.github.muer.audit.AuditRecord;
import io.github.muer.audit.AuditSubjectLink;
import io.github.muer.persistence.AuditEventRow;
import io.github.muer.persistence.AuditSubjectLinkRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface IamAuditMapper {
    int insertAudit(@Param("record") AuditRecord record,
                    @Param("beforeJson") String beforeJson,
                    @Param("afterJson") String afterJson,
                    @Param("metadataJson") String metadataJson);

    Long findDatabaseId(@Param("auditId") String auditId);

    int insertSubject(@Param("auditLogId") long auditLogId,
                      @Param("subject") AuditSubjectLink subject);

    AuditEventRow findEventByEventId(@Param("eventId") String eventId);

    /**
     * Newest-first audit event search whose internal log id is less than
     * {@code maxLogId}. Subject filters rely on the subject link convention
     * USER/IDENTITY/SESSION written by the audit producer.
     */
    List<AuditEventRow> findEvents(@Param("maxLogId") long maxLogId,
                                   @Param("limit") int limit,
                                   @Param("actionCode") String actionCode,
                                   @Param("resourceType") String resourceType,
                                   @Param("resourceId") String resourceId,
                                   @Param("occurredFrom") Instant occurredFrom,
                                   @Param("occurredTo") Instant occurredTo,
                                   @Param("subjectUserId") String subjectUserId,
                                   @Param("subjectIdentityId") String subjectIdentityId,
                                   @Param("subjectSessionId") String subjectSessionId);

    List<AuditSubjectLinkRow> findSubjects(@Param("logIds") List<Long> logIds);
}
