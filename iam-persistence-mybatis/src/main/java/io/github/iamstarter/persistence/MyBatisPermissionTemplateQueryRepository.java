package io.github.iamstarter.persistence;

import io.github.iamstarter.authorization.PermissionSummary;
import io.github.iamstarter.authorization.PermissionSummaryPage;
import io.github.iamstarter.authorization.PermissionTemplate;
import io.github.iamstarter.authorization.PermissionTemplateQueryRepository;
import io.github.iamstarter.authorization.PermissionTemplateVersion;
import io.github.iamstarter.authorization.TemplateVersionStatus;
import io.github.iamstarter.persistence.mapper.IamPermissionTemplateQueryMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MyBatisPermissionTemplateQueryRepository implements PermissionTemplateQueryRepository {
    private final SqlSessionFactory sessions;

    public MyBatisPermissionTemplateQueryRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public Optional<PermissionTemplate> findTemplate(long templateId) {
        try (var session = sessions.openSession()) {
            var row = session.getMapper(IamPermissionTemplateQueryMapper.class).findTemplate(templateId);
            return row == null ? Optional.empty() : Optional.of(row.toDomain());
        }
    }

    @Override
    public List<PermissionTemplate> listTemplates(long afterTemplateId, int limit) {
        if (afterTemplateId < 0) throw new IllegalArgumentException("afterTemplateId must not be negative");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        try (var session = sessions.openSession()) {
            return session.getMapper(IamPermissionTemplateQueryMapper.class)
                    .listTemplates(afterTemplateId, limit).stream().map(PermissionTemplateRow::toDomain).toList();
        }
    }

    @Override
    public List<PermissionTemplateVersion> findVersionsByTemplate(long templateId) {
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(IamPermissionTemplateQueryMapper.class);
            return mapper.listVersions(templateId).stream()
                    .map(version -> new PermissionTemplateVersion(version.versionId(), version.templateId(),
                            version.versionNumber(), version.status(),
                            Set.copyOf(mapper.findPermissions(version.versionId()))))
                    .toList();
        }
    }

    @Override
    public Optional<PermissionTemplateVersion> findVersionById(long versionId) {
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(IamPermissionTemplateQueryMapper.class);
            var rows = mapper.findVersion(versionId);
            if (rows.isEmpty()) return Optional.empty();
            var version = rows.getFirst();
            return Optional.of(new PermissionTemplateVersion(version.versionId(), version.templateId(),
                    version.versionNumber(), version.status(),
                    Set.copyOf(mapper.findPermissions(version.versionId()))));
        }
    }

    @Override
    public PermissionSummaryPage listPermissions(String keyword, String domain,
                                                 long afterPermissionId, int limit) {
        if (afterPermissionId < 0) throw new IllegalArgumentException("afterPermissionId must not be negative");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        try (var session = sessions.openSession()) {
            var rows = session.getMapper(IamPermissionTemplateQueryMapper.class)
                    .listPermissions(afterPermissionId, limit, blankToNull(keyword), blankToNull(domain));
            if (rows.isEmpty()) return new PermissionSummaryPage(List.of(), afterPermissionId);
            var items = new ArrayList<PermissionSummary>(rows.size());
            for (var row : rows) items.add(row.toDomain());
            return new PermissionSummaryPage(List.copyOf(items), rows.getLast().permissionId());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
