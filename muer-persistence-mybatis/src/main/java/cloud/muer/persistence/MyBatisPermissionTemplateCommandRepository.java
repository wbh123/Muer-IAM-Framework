package cloud.muer.persistence;

import cloud.muer.authorization.PermissionTemplate;
import cloud.muer.authorization.PermissionTemplateCommandRepository;
import cloud.muer.authorization.PermissionTemplateVersion;
import cloud.muer.authorization.TemplateVersionStatus;
import cloud.muer.persistence.mapper.IamPermissionTemplateVersionMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Objects;
import java.util.Set;

/** MyBatis command implementation which owns generated template identifiers and version numbers. */
public final class MyBatisPermissionTemplateCommandRepository implements PermissionTemplateCommandRepository {
    private final SqlSessionFactory sessions;

    public MyBatisPermissionTemplateCommandRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public PermissionTemplate createTemplate(String templateKey, String name, String description, boolean enabled) {
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(IamPermissionTemplateVersionMapper.class);
            mapper.insertTemplate(templateKey, name, description, enabled);
            long id = mapper.lastInsertId();
            session.commit();
            return new PermissionTemplate(id, templateKey, name, description, enabled);
        }
    }

    @Override
    public PermissionTemplateVersion createNextDraftVersion(long templateId, Set<String> permissions) {
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(IamPermissionTemplateVersionMapper.class);
            int number = mapper.nextVersionNumberForUpdate(templateId);
            mapper.insertDraftVersion(templateId, number);
            long id = mapper.lastInsertId();
            for (String permission : permissions.stream().sorted().toList()) {
                mapper.upsertPermission(permission);
                mapper.insertPermissionLink(id, permission);
            }
            session.commit();
            return new PermissionTemplateVersion(id, templateId, number, TemplateVersionStatus.DRAFT, permissions);
        }
    }
}
