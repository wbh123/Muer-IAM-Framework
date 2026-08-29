package io.github.iamstarter.authorization;

public interface AuthorizationVersionRepository {
    long currentVersion(long userId);
    long increment(long userId);
}
