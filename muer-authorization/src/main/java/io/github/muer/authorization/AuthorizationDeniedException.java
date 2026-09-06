package io.github.iamstarter.authorization;

public final class AuthorizationDeniedException extends RuntimeException {
    private final AuthorizationDecision decision;

    public AuthorizationDeniedException(AuthorizationDecision decision) {
        super(decision.decisionCode());
        this.decision = decision;
    }

    public AuthorizationDecision decision() { return decision; }
}
