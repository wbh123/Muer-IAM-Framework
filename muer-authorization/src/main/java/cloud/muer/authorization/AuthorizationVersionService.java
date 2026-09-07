package io.github.muer.authorization;

import io.github.muer.core.model.IamPrincipal;

import java.util.Objects;

public final class AuthorizationVersionService {
    private final AuthorizationVersionRepository repository;

    public AuthorizationVersionService(AuthorizationVersionRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public long increment(long userId) {
        return repository.increment(userId);
    }

    public boolean isCurrent(IamPrincipal principal) {
        return repository.currentVersion(principal.userId()) == principal.authorizationVersion();
    }
}
