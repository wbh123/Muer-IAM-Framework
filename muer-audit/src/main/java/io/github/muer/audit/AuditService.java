package io.github.iamstarter.audit;

import java.util.List;
import java.util.Objects;

public final class AuditService {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void record(AuditRecord record, List<AuditSubjectLink> subjects) {
        repository.append(Objects.requireNonNull(record), subjects == null ? List.of() : List.copyOf(subjects));
    }
}
