package io.github.iamstarter.authentication;

import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.authorization.AuthorizationProfile;
import io.github.iamstarter.session.TokenStore;
import io.github.iamstarter.session.AuthSession;
import io.github.iamstarter.session.SessionRepository;
import io.github.iamstarter.session.LoginEvent;
import io.github.iamstarter.session.LoginEventRepository;
import io.github.iamstarter.session.LoginResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import io.github.iamstarter.session.TokenRecord;

public final class AuthenticationService {
    private final TokenStore tokens;
    private final LongUnaryOperator currentAuthorizationVersion;
    private final Clock clock;
    private final IdentityAuthenticator authenticator;
    private final Duration tokenTtl;
    private final Supplier<String> tokenSupplier;
    private final Supplier<String> sessionSupplier;
    private final SessionRepository sessions;
    private final Duration sessionTouchInterval;
    private final LoginEventRepository loginEvents;
    private final Supplier<String> loginEventIdSupplier;

    public AuthenticationService(TokenStore tokens, LongUnaryOperator currentAuthorizationVersion) {
        this(tokens, currentAuthorizationVersion, request -> Optional.empty(), Clock.systemUTC(),
                Duration.ofHours(8), AuthenticationService::randomId, AuthenticationService::randomId);
    }

    public AuthenticationService(TokenStore tokens, LongUnaryOperator currentAuthorizationVersion,
                                 IdentityAuthenticator authenticator, Clock clock, Duration tokenTtl,
                                 Supplier<String> tokenSupplier, Supplier<String> sessionSupplier) {
        this(tokens, currentAuthorizationVersion, authenticator, clock, tokenTtl, tokenSupplier, sessionSupplier,
                null, Duration.ofMinutes(10),
                event -> { }, AuthenticationService::randomId);
    }

    public AuthenticationService(TokenStore tokens, SessionRepository sessions,
                                 LongUnaryOperator currentAuthorizationVersion,
                                 IdentityAuthenticator authenticator, Clock clock, Duration tokenTtl,
                                 Supplier<String> tokenSupplier, Supplier<String> sessionSupplier) {
        this(tokens, currentAuthorizationVersion, authenticator, clock, tokenTtl, tokenSupplier, sessionSupplier,
                Objects.requireNonNull(sessions), Duration.ofMinutes(10),
                event -> { }, AuthenticationService::randomId);
    }

    public AuthenticationService(TokenStore tokens, SessionRepository sessions,
                                 LongUnaryOperator currentAuthorizationVersion,
                                 IdentityAuthenticator authenticator, Clock clock, Duration tokenTtl,
                                 Duration sessionTouchInterval,
                                 Supplier<String> tokenSupplier, Supplier<String> sessionSupplier) {
        this(tokens, currentAuthorizationVersion, authenticator, clock, tokenTtl, tokenSupplier, sessionSupplier,
                Objects.requireNonNull(sessions), sessionTouchInterval,
                event -> { }, AuthenticationService::randomId);
    }

    public AuthenticationService(TokenStore tokens, SessionRepository sessions, LoginEventRepository loginEvents,
                                 LongUnaryOperator currentAuthorizationVersion,
                                 IdentityAuthenticator authenticator, Clock clock, Duration tokenTtl,
                                 Duration sessionTouchInterval, Supplier<String> loginEventIdSupplier,
                                 Supplier<String> tokenSupplier, Supplier<String> sessionSupplier) {
        this(tokens, currentAuthorizationVersion, authenticator, clock, tokenTtl, tokenSupplier, sessionSupplier,
                Objects.requireNonNull(sessions), sessionTouchInterval,
                loginEvents, loginEventIdSupplier);
    }

    private AuthenticationService(TokenStore tokens, LongUnaryOperator currentAuthorizationVersion,
                                  IdentityAuthenticator authenticator, Clock clock, Duration tokenTtl,
                                  Supplier<String> tokenSupplier, Supplier<String> sessionSupplier,
                                  SessionRepository sessions,
                                  Duration sessionTouchInterval, LoginEventRepository loginEvents,
                                  Supplier<String> loginEventIdSupplier) {
        this.tokens = Objects.requireNonNull(tokens);
        this.currentAuthorizationVersion = Objects.requireNonNull(currentAuthorizationVersion);
        this.authenticator = Objects.requireNonNull(authenticator);
        this.clock = Objects.requireNonNull(clock);
        this.tokenTtl = Objects.requireNonNull(tokenTtl);
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier);
        this.sessionSupplier = Objects.requireNonNull(sessionSupplier);
        this.sessions = sessions;
        this.sessionTouchInterval = requirePositive(sessionTouchInterval, "sessionTouchInterval");
        this.loginEvents = Objects.requireNonNull(loginEvents);
        this.loginEventIdSupplier = Objects.requireNonNull(loginEventIdSupplier);
    }

    public Optional<AuthenticationResult> login(LoginRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Optional<IamPrincipal> principal;
        try {
            principal = authenticator.authenticate(request);
        } catch (RuntimeException exception) {
            appendFailurePreserving(request, "AUTHENTICATOR_ERROR", exception);
            throw exception;
        }
        if (principal.isEmpty()) {
            loginEvents.append(loginEvent(request, null, null, LoginResult.FAILED, "CREDENTIAL_REJECTED"));
            return Optional.empty();
        }
        AuthenticationResult result;
        try {
            result = issue(principal.orElseThrow(), request.clientInstance(), request.ipAddress(), request.userAgent());
        } catch (RuntimeException exception) {
            appendFailurePreserving(request, "TOKEN_ISSUE_ERROR", exception);
            throw exception;
        }
        try {
            loginEvents.append(loginEvent(request, result.principal(), result.sessionId(), LoginResult.SUCCESS, null));
        } catch (RuntimeException exception) {
            compensateFailedLoginAudit(result, exception);
            throw exception;
        }
        return Optional.of(result);
    }

    AuthenticationResult issueForProfile(IamPrincipal current, AuthorizationProfile profile) {
        var switched = new IamPrincipal(
                current.userId(),
                current.identityId(),
                current.identityDomain(),
                profile.profileId(),
                profile.templateVersionId(),
                current.clientType(),
                currentAuthorizationVersion.applyAsLong(current.userId()));
        return issue(switched, null);
    }

    public Optional<IamPrincipal> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return tokens.resolve(token)
                .filter(record -> record.expiresAt().isAfter(clock.instant()))
                .filter(record -> currentAuthorizationVersion.applyAsLong(record.principal().userId())
                        == record.principal().authorizationVersion())
                .map(record -> {
                    var occurredAt = clock.instant();
                    if (sessions != null && tokens.acquireSessionTouchLease(record.sessionId(), sessionTouchInterval)) {
                        try {
                            sessions.touch(record.sessionId(), occurredAt);
                        } catch (RuntimeException ignored) {
                            // Activity projection failure must not invalidate an otherwise valid credential.
                        }
                    }
                    return record.principal();
                });
    }

    private AuthenticationResult issue(IamPrincipal principal, String clientInstance) {
        return issue(principal, clientInstance, null, null);
    }

    private AuthenticationResult issue(IamPrincipal principal, String clientInstance,
                                       String ipAddress, String userAgent) {
        String token = tokenSupplier.get();
        String sessionId = sessionSupplier.get();
        var issuedAt = clock.instant();
        var expiresAt = issuedAt.plus(tokenTtl);
        tokens.save(token, new TokenRecord(sessionId, principal, expiresAt), tokenTtl);
        try {
            if (sessions != null) {
                sessions.save(new AuthSession(sessionId, principal.userId(), principal.clientType(),
                        clientInstance, ipAddress, userAgent, issuedAt, issuedAt,
                        expiresAt, null, null, null));
            }
        } catch (RuntimeException exception) {
            tokens.revoke(token);
            throw exception;
        }
        return new AuthenticationResult(token, sessionId, expiresAt, principal);
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private LoginEvent loginEvent(LoginRequest request, IamPrincipal principal, String sessionId,
                                  LoginResult result, String reasonCode) {
        return new LoginEvent(loginEventIdSupplier.get(), principal == null ? null : principal.userId(),
                request.username(), principal == null ? null : principal.identityDomain(), request.clientType(),
                request.clientInstance(), request.ipAddress(), request.userAgent(), request.deviceType(),
                request.osName(), request.browserName(), request.appVersion(), sessionId, result, reasonCode,
                request.requestId(), clock.instant());
    }

    private void appendFailurePreserving(LoginRequest request, String reasonCode, RuntimeException original) {
        try {
            loginEvents.append(loginEvent(request, null, null, LoginResult.FAILED, reasonCode));
        } catch (RuntimeException auditFailure) {
            original.addSuppressed(auditFailure);
        }
    }

    private void compensateFailedLoginAudit(AuthenticationResult result, RuntimeException original) {
        try {
            tokens.revoke(result.accessToken());
        } catch (RuntimeException tokenFailure) {
            original.addSuppressed(tokenFailure);
        }
        if (sessions == null) return;
        try {
            sessions.findById(result.sessionId()).ifPresent(session ->
                    sessions.save(session.revoke(clock.instant(), "LOGIN_AUDIT_FAILED")));
        } catch (RuntimeException sessionFailure) {
            original.addSuppressed(sessionFailure);
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
