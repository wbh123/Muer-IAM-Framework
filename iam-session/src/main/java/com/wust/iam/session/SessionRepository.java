package com.wust.iam.session;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface SessionRepository {
    List<AuthSession> activeForUser(long userId);
    Optional<AuthSession> findById(String sessionId);
    void save(AuthSession session);

    default void touch(String sessionId, Instant lastSeenAt) {
        findById(sessionId).map(value -> value.touch(lastSeenAt)).ifPresent(this::save);
    }
}
