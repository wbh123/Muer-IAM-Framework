package io.github.iamstarter.persistence;

import io.github.iamstarter.audit.AuditEvent;
import io.github.iamstarter.audit.AuditEventFilter;
import io.github.iamstarter.audit.AuditEventPage;
import io.github.iamstarter.audit.AuditQueryRepository;
import io.github.iamstarter.audit.AuditSubjectLink;
import io.github.iamstarter.persistence.mapper.IamAuditMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisAuditQueryRepository implements AuditQueryRepository {
    private final SqlSessionFactory sessions;
    private final JsonObjectDecoder json = new JsonObjectDecoder();

    public MyBatisAuditQueryRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public Optional<AuditEvent> findById(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(IamAuditMapper.class);
            var row = mapper.findEventByEventId(eventId);
            if (row == null) return Optional.empty();
            return Optional.of(toEvent(row, groupSubjects(mapper.findSubjects(List.of(row.logId())))
                    .getOrDefault(row.logId(), List.of())));
        }
    }

    @Override
    public AuditEventPage findEvents(AuditEventFilter filter, long maxLogId, int limit) {
        Objects.requireNonNull(filter, "filter must not be null");
        if (maxLogId < 0) throw new IllegalArgumentException("maxLogId must not be negative");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(IamAuditMapper.class);
            var rows = mapper.findEvents(maxLogId, limit + 1, filter.eventType(),
                    filter.resourceType(), filter.resourceId(), filter.from(), filter.to(),
                    filter.userId() == null ? null : filter.userId().toString(),
                    filter.identityId(), filter.sessionId());
            boolean hasMore = rows.size() > limit;
            if (hasMore) rows = rows.subList(0, limit);
            if (rows.isEmpty()) return new AuditEventPage(List.of(), maxLogId);
            var subjects = groupSubjects(mapper.findSubjects(
                    rows.stream().map(AuditEventRow::logId).toList()));
            var events = new ArrayList<AuditEvent>(rows.size());
            for (var row : rows) {
                events.add(toEvent(row, subjects.getOrDefault(row.logId(), List.of())));
            }
            return new AuditEventPage(List.copyOf(events), rows.getLast().logId());
        }
    }

    private AuditEvent toEvent(AuditEventRow row, List<AuditSubjectLinkRow> subjectRows) {
        var subjects = new ArrayList<AuditSubjectLink>(subjectRows.size());
        for (var subject : subjectRows) subjects.add(subject.toDomain());
        return new AuditEvent(row.eventId(), row.occurredAt(), row.actor(), row.action(),
                row.resourceType(), row.resourceId(), row.result(), row.requestId(),
                json.decode(row.beforeJson()), json.decode(row.afterJson()),
                json.decode(row.metadataJson()), List.copyOf(subjects));
    }

    private static Map<Long, List<AuditSubjectLinkRow>> groupSubjects(List<AuditSubjectLinkRow> links) {
        var grouped = new HashMap<Long, List<AuditSubjectLinkRow>>();
        for (var link : links) {
            grouped.computeIfAbsent(link.logId(), ignored -> new ArrayList<>()).add(link);
        }
        return grouped;
    }
}
