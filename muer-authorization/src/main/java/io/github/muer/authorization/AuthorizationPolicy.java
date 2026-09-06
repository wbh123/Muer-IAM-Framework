package io.github.muer.authorization;

import io.github.muer.core.model.IamPrincipal;

@FunctionalInterface
public interface AuthorizationPolicy {
    AuthorizationPolicyResult evaluate(IamPrincipal principal, AuthorizationRequest request);
}
