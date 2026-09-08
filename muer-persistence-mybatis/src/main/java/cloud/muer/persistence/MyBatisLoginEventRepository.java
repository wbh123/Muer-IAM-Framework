package cloud.muer.persistence;

import cloud.muer.persistence.mapper.IamLoginEventMapper;
import cloud.muer.session.LoginEvent;
import cloud.muer.session.LoginEventRepository;
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
