package cloud.muer.authentication;

import cloud.muer.core.model.IamPrincipal;

import java.time.Instant;

public record AuthenticationResult(String accessToken, String sessionId, Instant expiresAt, IamPrincipal principal) { }
