package io.github.muer.persistence;

import io.github.muer.authorization.PermissionDefinition;
import io.github.muer.authorization.PermissionRepository;
import io.github.muer.persistence.mapper.IamPermissionMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Objects;

/** MyBatis implementation that refreshes only provider-owned permission metadata. */
public final class MyBatisPermissionRepository implements PermissionRepository {
    private final SqlSessionFactory sessions;

    public MyBatisPermissionRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public void upsert(PermissionDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        try (var session = sessions.openSession(false)) {
            session.getMapper(IamPermissionMapper.class).upsert(definition);
            session.commit();
        }
    }
}
