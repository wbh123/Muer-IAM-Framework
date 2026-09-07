package io.github.muer.persistence;

import io.github.muer.audit.AuditSubjectLink;
import io.github.muer.audit.AuditSubjectRelation;

/**
 * Flat projection of one audit subject link including its parent audit log id.
 */
public record AuditSubjectLinkRow(long logId, String subjectType, String subjectId,
                                  AuditSubjectRelation relation) {
    public AuditSubjectLink toDomain() {
        return new AuditSubjectLink(subjectType, subjectId, relation);
    }
}
