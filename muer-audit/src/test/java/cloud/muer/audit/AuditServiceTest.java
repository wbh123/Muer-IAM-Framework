package io.github.muer.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditServiceTest {
    @Test
    void records_one_event_with_multiple_generic_subject_links() {
        var repository = new RecordingRepository();
        var service = new AuditService(repository);
        var record = new AuditRecord("audit-1", "user:7", "profile.scope.replace", "PROFILE", "19",
                "SUCCESS", "request-1", Map.of("old", "7"), Map.of("new", "12"),
                Map.of("reason", "rotation"), Instant.parse("2026-08-26T00:00:00Z"));
        var subjects = List.of(new AuditSubjectLink("PROFILE", "19", AuditSubjectRelation.PRIMARY),
                new AuditSubjectLink("USER", "7", AuditSubjectRelation.AFFECTED));

        service.record(record, subjects);

        assertEquals(record, repository.record);
        assertEquals(subjects, repository.subjects);
    }

    private static final class RecordingRepository implements AuditRepository {
        private AuditRecord record;
        private List<AuditSubjectLink> subjects = new ArrayList<>();
        public void append(AuditRecord record, List<AuditSubjectLink> subjects) {
            this.record = record;
            this.subjects = List.copyOf(subjects);
        }
    }
}
