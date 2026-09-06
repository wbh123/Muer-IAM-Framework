package io.github.muer.persistence;

import io.github.muer.core.model.OverviewMetrics;
import io.github.muer.core.port.OverviewRepository;
import io.github.muer.persistence.mapper.IamOverviewMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Objects;

public final class MyBatisOverviewRepository implements OverviewRepository {
    private final SqlSessionFactory sessions;

    public MyBatisOverviewRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public OverviewMetrics load() {
        try (var session = sessions.openSession()) {
            return session.getMapper(IamOverviewMapper.class).load().toDomain();
        }
    }
}
