package com.wust.iam.persistence;

import com.wust.iam.audit.AuditRecord;
import com.wust.iam.audit.AuditRepository;
import com.wust.iam.audit.AuditSubjectLink;
import com.wust.iam.persistence.mapper.IamAuditMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.Objects;

public final class MyBatisAuditRepository implements AuditRepository {
    private final SqlSessionFactory sessions;
    private final JsonObjectEncoder json = new JsonObjectEncoder();

    public MyBatisAuditRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public void append(AuditRecord record, List<AuditSubjectLink> subjects) {
        Objects.requireNonNull(record, "record must not be null");
        var immutableSubjects = subjects == null ? List.<AuditSubjectLink>of() : List.copyOf(subjects);
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(IamAuditMapper.class);
            mapper.insertAudit(record, json.encode(record.before()), json.encode(record.after()),
                    json.encode(record.metadata()));
            long auditLogId = mapper.findDatabaseId(record.auditId());
            immutableSubjects.forEach(subject -> mapper.insertSubject(auditLogId, subject));
            session.commit();
        }
    }
}
