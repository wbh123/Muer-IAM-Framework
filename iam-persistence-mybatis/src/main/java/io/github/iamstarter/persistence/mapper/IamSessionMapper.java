package io.github.iamstarter.persistence.mapper;

import io.github.iamstarter.session.AuthSession;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface IamSessionMapper {
    Optional<AuthSession> findById(@Param("sessionId") String sessionId);

    List<AuthSession> findActiveForUser(@Param("userId") long userId);

    int insert(AuthSession session);

    int update(AuthSession session);

    int touch(@Param("sessionId") String sessionId, @Param("lastSeenAt") Instant lastSeenAt);
}
