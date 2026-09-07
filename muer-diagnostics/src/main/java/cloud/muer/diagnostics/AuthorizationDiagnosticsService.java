package io.github.muer.diagnostics;

import io.github.muer.authorization.AuthorizationEngine;
import io.github.muer.authorization.AuthorizationRequest;
import io.github.muer.core.model.IamPrincipal;

import java.util.Objects;

public final class AuthorizationDiagnosticsService {
    private final AuthorizationEngine engine;

    public AuthorizationDiagnosticsService(AuthorizationEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    public AuthorizationDiagnosisProjection evaluate(IamPrincipal principal, AuthorizationRequest request) {
        return AuthorizationDiagnosisProjection.from(engine.decide(principal, request));
    }
}
