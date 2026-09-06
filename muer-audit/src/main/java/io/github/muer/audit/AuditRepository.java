package io.github.iamstarter.audit;

import java.util.List;

public interface AuditRepository {
    void append(AuditRecord record, List<AuditSubjectLink> subjects);
}
