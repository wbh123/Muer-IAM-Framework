package cloud.muer.authorization;

import java.util.List;

public record AuthorizationDecision(boolean allowed, String decisionCode, List<AuthorizationDecisionStep> steps) {
    public AuthorizationDecision { steps = steps == null ? List.of() : List.copyOf(steps); }
}
