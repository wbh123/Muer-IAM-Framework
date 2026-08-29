package com.wust.iam.authentication;

import com.wust.iam.core.model.IamPrincipal;

import java.time.Instant;

public record AuthenticationResult(String accessToken, String sessionId, Instant expiresAt, IamPrincipal principal) { }
