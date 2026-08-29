package io.github.iamstarter.authorization;

import io.github.iamstarter.core.model.ResourceScope;

import java.util.List;

/** Atomically replaces profile scopes and invalidates credentials issued under the old version. */
@FunctionalInterface
public interface AuthorizationScopeMutation {
    void replaceScopesAndIncrementVersion(long profileId, List<ResourceScope> scopes);
}
