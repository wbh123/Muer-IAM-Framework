package io.github.iamstarter.authorization;

import io.github.iamstarter.core.model.IamPrincipal;

@FunctionalInterface
public interface AuthorizationPolicy {
    AuthorizationPolicyResult evaluate(IamPrincipal principal, AuthorizationRequest request);
}
