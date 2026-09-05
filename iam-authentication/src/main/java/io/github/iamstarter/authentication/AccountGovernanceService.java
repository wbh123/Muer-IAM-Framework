package io.github.iamstarter.authentication;

import io.github.iamstarter.authorization.AuthorizationVersionService;
import io.github.iamstarter.core.model.IamUser;
import io.github.iamstarter.core.model.Identity;
import io.github.iamstarter.core.port.IamUserRepository;
import io.github.iamstarter.core.port.IdentityRepository;
import io.github.iamstarter.core.port.UserQueryRepository;

import java.util.List;
import java.util.Objects;

public final class AccountGovernanceService {
    private final IamUserRepository users;
    private final IdentityRepository identities;
    private final AuthorizationVersionService versions;
    private final UserQueryRepository query;

    public AccountGovernanceService(IamUserRepository users, IdentityRepository identities,
                                    AuthorizationVersionService versions) {
        this(users, identities, versions, null);
    }

    public AccountGovernanceService(IamUserRepository users, IdentityRepository identities,
                                    AuthorizationVersionService versions, UserQueryRepository query) {
        this.users = Objects.requireNonNull(users);
        this.identities = Objects.requireNonNull(identities);
        this.versions = Objects.requireNonNull(versions);
        this.query = query;
    }

    public List<IamUser> listUsers(long afterUserId, int limit) {
        if (afterUserId < 0) throw new IllegalArgumentException("afterUserId must not be negative");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return List.copyOf(users.findPage(afterUserId, limit));
    }

    public List<IamUser> listUsers(long afterUserId, int limit, String username, String userType,
                                   Boolean enabled) {
        if (query == null) {
            throw new IllegalArgumentException("user filtering is not available on this IAM backend");
        }
        return List.copyOf(query.search(afterUserId, limit, username, userType, enabled));
    }

    public List<Identity> listIdentities(long userId) {
        users.require(userId);
        return List.copyOf(identities.findByUserId(userId));
    }

    public void saveUser(IamUser replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        var existing = users.findById(replacement.userId());
        users.save(replacement);
        if (existing.isPresent()) versions.increment(replacement.userId());
    }

    public void saveUser(long userId, String username, String userType, boolean enabled) {
        long authorizationVersion = users.findById(userId)
                .map(IamUser::authorizationVersion)
                .orElse(1L);
        saveUser(new IamUser(userId, username, userType, enabled, authorizationVersion));
    }

    public void saveIdentity(Identity replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        users.require(replacement.userId());
        identities.findById(replacement.identityId()).ifPresent(existing -> {
            if (existing.userId() != replacement.userId()) {
                throw new IllegalArgumentException("identity ownership cannot be changed");
            }
        });
        identities.save(replacement);
        versions.increment(replacement.userId());
    }
}
