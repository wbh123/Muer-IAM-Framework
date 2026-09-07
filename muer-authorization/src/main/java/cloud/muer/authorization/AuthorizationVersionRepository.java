package io.github.muer.authorization;

public interface AuthorizationVersionRepository {
    long currentVersion(long userId);
    long increment(long userId);
}
