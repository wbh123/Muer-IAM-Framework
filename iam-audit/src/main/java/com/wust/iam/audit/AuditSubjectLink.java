package com.wust.iam.audit;

import java.util.Objects;

public record AuditSubjectLink(String subjectType, String subjectId, AuditSubjectRelation relation) {
    public AuditSubjectLink {
        subjectType = required(subjectType, "subjectType");
        subjectId = required(subjectId, "subjectId");
        relation = Objects.requireNonNull(relation, "relation must not be null");
    }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
