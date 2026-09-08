package cloud.muer.persistence;

import cloud.muer.authorization.PermissionTemplateVersion;
import cloud.muer.authorization.PermissionTemplateVersionRepository;
import cloud.muer.persistence.mapper.IamPermissionTemplateVersionMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public final class MyBatisPermissionTemplateVersionRepository implements PermissionTemplateVersionRepository {
    private final SqlSessionFactory sessions;

    public MyBatisPermissionTemplateVersionRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public PermissionTemplateVersion require(long versionId) {
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(IamPermissionTemplateVersionMapper.class);
            var row = mapper.findVersion(versionId);
            if (row == null) throw new NoSuchElementException("permission template version not found: " + versionId);
            return new PermissionTemplateVersion(row.versionId(), row.templateId(), row.versionNumber(), row.status(),
                    Set.copyOf(mapper.findPermissions(versionId)));
        }
    }

    @Override
    public void save(PermissionTemplateVersion version) {
        Objects.requireNonNull(version, "version must not be null");
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(IamPermissionTemplateVersionMapper.class);
            if (mapper.exists(version.versionId()) == 0) mapper.insertVersion(version);
            else mapper.updateVersion(version);
            mapper.deletePermissionLinks(version.versionId());
            version.permissions().stream().sorted().forEach(permissionCode -> {
                mapper.upsertPermission(permissionCode);
                mapper.insertPermissionLink(version.versionId(), permissionCode);
            });
            session.commit();
        }
    }
}
