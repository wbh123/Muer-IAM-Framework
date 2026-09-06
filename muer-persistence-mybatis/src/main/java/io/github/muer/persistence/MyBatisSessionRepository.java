package io.github.muer.persistence;

import io.github.muer.persistence.mapper.IamSessionMapper;
import io.github.muer.session.AuthSession;
import io.github.muer.session.SessionRepository;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;

public final class MyBatisSessionRepository implements SessionRepository {
    private final SqlSessionFactory sessions;

    public MyBatisSessionRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public List<AuthSession> activeForUser(long userId) {
        try (var session = sessions.openSession()) {
            return List.copyOf(session.getMapper(IamSessionMapper.class).findActiveForUser(userId));
        }
    }

    @Override
    public Optional<AuthSession> findById(String sessionId) {
        try (var session = sessions.openSession()) {
            return session.getMapper(IamSessionMapper.class).findById(sessionId);
        }
    }

    @Override
    public void save(AuthSession value) {
        Objects.requireNonNull(value, "session must not be null");
        try (var session = sessions.openSession(false)) {
            var mapper = session.getMapper(IamSessionMapper.class);
            if (mapper.findById(value.sessionId()).isPresent()) mapper.update(value);
            else mapper.insert(value);
            session.commit();
        }
    }

    @Override
    public void touch(String sessionId, Instant lastSeenAt) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
        try (var session = sessions.openSession(false)) {
            session.getMapper(IamSessionMapper.class).touch(sessionId, lastSeenAt);
            session.commit();
        }
    }
}
