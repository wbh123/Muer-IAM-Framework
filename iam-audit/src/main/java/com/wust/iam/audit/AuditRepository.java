package com.wust.iam.audit;

import java.util.List;

public interface AuditRepository {
    void append(AuditRecord record, List<AuditSubjectLink> subjects);
}
