package cloud.muer.persistence;

import cloud.muer.authorization.AuthorizationVersionRepository;
import cloud.muer.persistence.mapper.IamAuthorizationVersionMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.NoSuchElementException;
import java.util.Objects;

public final class MyBatisAuthorizationVersionRepository implements AuthorizationVersionRepository {
    private final SqlSessionFactory sessions;

    public MyBatisAuthorizationVersionRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public long currentVersion(long userId) {
        try (var session = sessions.openSession()) {
            return requireVersion(session.getMapper(IamAuthorizationVersionMapper.class).findVersion(userId), userId);
        }
    }

    @Override
    public long increment(long userId) {
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(IamAuthorizationVersionMapper.class);
            if (mapper.increment(userId) != 1) throw new NoSuchElementException("IAM user not found: " + userId);
            long version = requireVersion(mapper.findVersion(userId), userId);
            session.commit();
            return version;
        }
    }

    private static long requireVersion(Long version, long userId) {
        if (version == null) throw new NoSuchElementException("IAM user not found: " + userId);
        return version;
    }
}
