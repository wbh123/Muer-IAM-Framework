package io.github.iamstarter.persistence;

import io.github.iamstarter.persistence.mapper.IamLoginEventMapper;
import io.github.iamstarter.session.LoginEvent;
import io.github.iamstarter.session.LoginEventRepository;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Objects;

public final class MyBatisLoginEventRepository implements LoginEventRepository {
    private final SqlSessionFactory sessions;

    public MyBatisLoginEventRepository(SqlSessionFactory sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    }

    @Override
    public void append(LoginEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try (var session = sessions.openSession(false)) {
            session.getMapper(IamLoginEventMapper.class).insert(event);
            session.commit();
        }
    }
}
