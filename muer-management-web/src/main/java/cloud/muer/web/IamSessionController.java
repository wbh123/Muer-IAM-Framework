package cloud.muer.web;

import cloud.muer.core.model.IamPrincipal;
import cloud.muer.session.AuthSession;
import cloud.muer.session.SessionRepository;
import cloud.muer.session.SessionService;
import cloud.muer.web.api.SessionsApi;
import cloud.muer.web.dto.RevokeOthersRequest;
import cloud.muer.web.dto.RevokeRequest;
import cloud.muer.web.dto.SessionListResponse;
import cloud.muer.web.dto.SessionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Objects;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
public class IamSessionController implements SessionsApi {
    private static final String DEFAULT_REASON = "USER_REQUEST";
    private final SessionRepository sessions;
    private final SessionService sessionService;

    public IamSessionController(SessionRepository sessions, SessionService sessionService) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
    }

    @Override
    public ResponseEntity<SessionListResponse> listMySessions(Long ignoredUserId) {
        long userId = principal().userId();
        var items = sessions.activeForUser(userId).stream().map(IamSessionController::response).toList();
        return ResponseEntity.ok(new SessionListResponse(items));
    }

    @Override
    public ResponseEntity<Void> revokeMySession(String sessionId, RevokeRequest request) {
        sessionService.revoke(principal().userId(), sessionId,
                request == null || request.getReason() == null ? DEFAULT_REASON : request.getReason());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> revokeOtherSessions(RevokeOthersRequest request) {
        sessionService.revokeOthers(principal().userId(), request.getCurrentSessionId(),
                request.getReason() == null ? DEFAULT_REASON : request.getReason());
        return ResponseEntity.noContent().build();
    }

    private static IamPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof IamPrincipal principal)) {
            throw new ResponseStatusException(UNAUTHORIZED, "IAM authentication required");
        }
        return principal;
    }

    private static SessionResponse response(AuthSession session) {
        return new SessionResponse(session.sessionId(), session.userId(), session.clientType(),
                Date.from(session.loginAt()), Date.from(session.lastSeenAt()), Date.from(session.expiresAt()))
                .clientInstance(session.clientInstance())
                .ipAddress(session.ipAddress())
                .userAgent(session.userAgent())
                .logoutAt(session.logoutAt() == null ? null : Date.from(session.logoutAt()));
    }
}
