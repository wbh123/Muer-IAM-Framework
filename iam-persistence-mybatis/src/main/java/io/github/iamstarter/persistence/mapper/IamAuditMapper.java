package io.github.iamstarter.persistence.mapper;

import io.github.iamstarter.audit.AuditRecord;
import io.github.iamstarter.audit.AuditSubjectLink;
import org.apache.ibatis.annotations.Param;

public interface IamAuditMapper {
    int insertAudit(@Param("record") AuditRecord record,
                    @Param("beforeJson") String beforeJson,
                    @Param("afterJson") String afterJson,
                    @Param("metadataJson") String metadataJson);

    Long findDatabaseId(@Param("auditId") String auditId);

    int insertSubject(@Param("auditLogId") long auditLogId,
                      @Param("subject") AuditSubjectLink subject);
}
