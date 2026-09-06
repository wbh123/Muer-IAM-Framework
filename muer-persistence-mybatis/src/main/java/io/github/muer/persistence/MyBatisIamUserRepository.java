package io.github.iamstarter.persistence;

import io.github.iamstarter.core.model.IamUser;
import io.github.iamstarter.core.port.IamUserRepository;
import io.github.iamstarter.persistence.mapper.IamUserMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisIamUserRepository implements IamUserRepository {
    private final SqlSessionFactory sessions;
    public MyBatisIamUserRepository(SqlSessionFactory sessions) { this.sessions = Objects.requireNonNull(sessions); }
    public Optional<IamUser> findById(long userId) {
        try (var session = sessions.openSession()) { return session.getMapper(IamUserMapper.class).findById(userId); }
    }
    public List<IamUser> findPage(long afterUserId, int limit) {
        try (var session = sessions.openSession()) {
            return List.copyOf(session.getMapper(IamUserMapper.class).findPage(afterUserId, limit));
        }
    }
    public void save(IamUser user) {
        Objects.requireNonNull(user);
        try (var session = sessions.openSession(false)) {
            session.getMapper(IamUserMapper.class).upsert(user);
            session.commit();
        }
    }
}
