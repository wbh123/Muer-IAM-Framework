package cloud.muer.diagnostics;

import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.authorization.AuthorizationRequest;
import cloud.muer.core.model.IamPrincipal;

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
