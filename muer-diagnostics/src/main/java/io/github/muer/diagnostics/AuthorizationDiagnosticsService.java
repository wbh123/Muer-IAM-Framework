package io.github.iamstarter.diagnostics;

import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationRequest;
import io.github.iamstarter.core.model.IamPrincipal;

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
