package io.github.muer.persistence;

import io.github.muer.core.model.Identity;
import io.github.muer.core.port.IdentityRepository;
import io.github.muer.persistence.mapper.IamIdentityMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisIdentityRepository implements IdentityRepository {
    private final SqlSessionFactory sessions;
    public MyBatisIdentityRepository(SqlSessionFactory sessions) { this.sessions = Objects.requireNonNull(sessions); }
    public Optional<Identity> findById(String identityId) {
        try (var session = sessions.openSession()) {
            return session.getMapper(IamIdentityMapper.class).findById(identityId);
        }
    }
    public List<Identity> findByUserId(long userId) {
        try (var session = sessions.openSession()) {
            return List.copyOf(session.getMapper(IamIdentityMapper.class).findByUserId(userId));
        }
    }
    public void save(Identity identity) {
        Objects.requireNonNull(identity);
        try (var session = sessions.openSession(false)) {
            session.getMapper(IamIdentityMapper.class).upsert(identity);
            session.commit();
        }
    }
}
