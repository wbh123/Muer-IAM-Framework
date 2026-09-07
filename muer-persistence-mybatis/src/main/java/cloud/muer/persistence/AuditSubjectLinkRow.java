package cloud.muer.persistence;

import cloud.muer.audit.AuditSubjectLink;
import cloud.muer.audit.AuditSubjectRelation;

/**
 * Flat projection of one audit subject link including its parent audit log id.
 */
public record AuditSubjectLinkRow(long logId, String subjectType, String subjectId,
                                  AuditSubjectRelation relation) {
    public AuditSubjectLink toDomain() {
        return new AuditSubjectLink(subjectType, subjectId, relation);
    }
}
