package com.wust.iam.authorization;

public record AuthorizationDecisionStep(String code, boolean passed, String reason) { }
