package io.github.iamstarter.authentication;

import io.github.iamstarter.core.model.IamPrincipal;

import java.time.Instant;

public record AuthenticationResult(String accessToken, String sessionId, Instant expiresAt, IamPrincipal principal) { }
