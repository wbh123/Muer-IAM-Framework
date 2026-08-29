package io.github.iamstarter.authorization;

public record AuthorizationDecisionStep(String code, boolean passed, String reason) { }
