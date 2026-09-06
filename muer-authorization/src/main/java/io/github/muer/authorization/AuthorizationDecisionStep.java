package io.github.muer.authorization;

public record AuthorizationDecisionStep(String code, boolean passed, String reason) { }
