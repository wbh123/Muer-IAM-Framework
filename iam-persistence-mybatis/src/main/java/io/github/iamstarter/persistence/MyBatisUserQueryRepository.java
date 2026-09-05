package io.github.iamstarter.persistence;

import io.github.iamstarter.core.model.IamUser;
import io.github.iamstarter.core.port.UserQueryRepository;
import io.github.iamstarter.persistence.mapper.IamUserMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisUserQueryRepository implements UserQueryRepository {
    private final SqlSessionFactory sessions;

    public MyBatisUserQueryRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public Optional<IamUser> findById(long userId) {
        try (var session = sessions.openSession()) {
            return session.getMapper(IamUserMapper.class).findById(userId);
        }
    }

    @Override
    public List<IamUser> search(long afterUserId, int limit, String username, String userType, Boolean enabled) {
        if (afterUserId < 0) throw new IllegalArgumentException("afterUserId must not be negative");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        try (var session = sessions.openSession()) {
            return List.copyOf(session.getMapper(IamUserMapper.class)
                    .search(afterUserId, limit, blankToNull(username), blankToNull(userType), enabled));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
