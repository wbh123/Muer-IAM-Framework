package io.github.muer.persistence;

import io.github.muer.persistence.mapper.IamSessionMapper;
import io.github.muer.session.AuthSession;
import io.github.muer.session.SessionQueryRepository;
import org.apache.ibatis.session.SqlSessionFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisSessionQueryRepository implements SessionQueryRepository {
    private final SqlSessionFactory sessions;

    public MyBatisSessionQueryRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public Optional<AuthSession> findById(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try (var session = sessions.openSession()) {
            return session.getMapper(IamSessionMapper.class).findById(sessionId);
        }
    }

    @Override
    public List<AuthSession> findByUserId(long userId) {
        try (var session = sessions.openSession()) {
            return List.copyOf(session.getMapper(IamSessionMapper.class).findByUserId(userId));
        }
    }

    @Override
    public List<AuthSession> search(Instant afterLoginAt, String afterSessionId, int limit,
                                    Long userId, String clientType, Boolean active) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        if ((afterLoginAt == null) != (afterSessionId == null)) {
            throw new IllegalArgumentException("afterLoginAt and afterSessionId must be provided together");
        }
        try (var session = sessions.openSession()) {
            return List.copyOf(session.getMapper(IamSessionMapper.class)
                    .search(afterLoginAt, blankToNull(afterSessionId), limit, userId,
                            blankToNull(clientType), active));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
