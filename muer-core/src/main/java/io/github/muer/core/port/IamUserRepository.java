package io.github.iamstarter.core.port;

import io.github.iamstarter.core.model.IamUser;

import java.util.List;
import java.util.Optional;

public interface IamUserRepository {
    Optional<IamUser> findById(long userId);
    List<IamUser> findPage(long afterUserId, int limit);
    void save(IamUser user);

    default IamUser require(long userId) {
        return findById(userId).orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
    }
}
