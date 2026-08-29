package com.wust.iam.diagnostics;

import com.wust.iam.authorization.AuthorizationEngine;
import com.wust.iam.authorization.AuthorizationRequest;
import com.wust.iam.core.model.IamPrincipal;

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
