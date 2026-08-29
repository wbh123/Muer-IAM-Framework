package com.wust.iam.persistence.mapper;

import com.wust.iam.audit.AuditRecord;
import com.wust.iam.audit.AuditSubjectLink;
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
