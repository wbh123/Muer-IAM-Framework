package com.wust.iam.authorization;

public interface AuthorizationVersionRepository {
    long currentVersion(long userId);
    long increment(long userId);
}
