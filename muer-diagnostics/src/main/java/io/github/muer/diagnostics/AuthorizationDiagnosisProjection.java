package io.github.iamstarter.diagnostics;

import io.github.iamstarter.authorization.AuthorizationDecision;
import io.github.iamstarter.authorization.AuthorizationDecisionStep;

import java.util.List;

public record AuthorizationDiagnosisProjection(boolean allowed, String decisionCode,
                                               List<AuthorizationDecisionStep> steps) {
    public AuthorizationDiagnosisProjection {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static AuthorizationDiagnosisProjection from(AuthorizationDecision decision) {
        return new AuthorizationDiagnosisProjection(decision.allowed(), decision.decisionCode(), decision.steps());
    }
}
