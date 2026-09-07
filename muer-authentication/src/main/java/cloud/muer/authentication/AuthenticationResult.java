package io.github.muer.authentication;

import io.github.muer.core.model.IamPrincipal;

import java.time.Instant;

public record AuthenticationResult(String accessToken, String sessionId, Instant expiresAt, IamPrincipal principal) { }
