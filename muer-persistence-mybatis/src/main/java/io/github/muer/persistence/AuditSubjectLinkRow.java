package io.github.iamstarter.persistence;

import io.github.iamstarter.audit.AuditSubjectLink;
import io.github.iamstarter.audit.AuditSubjectRelation;

/**
 * Flat projection of one audit subject link including its parent audit log id.
 */
public record AuditSubjectLinkRow(long logId, String subjectType, String subjectId,
                                  AuditSubjectRelation relation) {
    public AuditSubjectLink toDomain() {
        return new AuditSubjectLink(subjectType, subjectId, relation);
    }
}
