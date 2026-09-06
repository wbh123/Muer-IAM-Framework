package io.github.muer.authorization;

import io.github.muer.core.model.IamPrincipal;

public interface AuthorizationEngine {
    AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request);

    default void require(IamPrincipal principal, AuthorizationRequest request) {
        var decision = decide(principal, request);
        if (!decision.allowed()) throw new AuthorizationDeniedException(decision);
    }
}
