package cloud.muer.web;

import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.core.port.UserQueryRepository;
import cloud.muer.session.AuthSession;
import cloud.muer.session.SessionQueryRepository;
import cloud.muer.web.api.ManagementSessionsApi;
import cloud.muer.web.dto.AdminSessionListResponse;
import cloud.muer.web.dto.SessionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static cloud.muer.web.WebSecurity.allowedRead;
import static cloud.muer.web.WebSecurity.currentPrincipal;

@RestController
public class IamManagementSessionsController implements ManagementSessionsApi {
    private static final String SESSION_READ = "iam.admin.session.read";
    private static final String CURSOR_SEPARATOR = ":";

    private final AuthorizationEngine authorization;
    private final SessionQueryRepository sessions;
    private final UserQueryRepository users;

    public IamManagementSessionsController(AuthorizationEngine authorization,
                                           SessionQueryRepository sessions,
                                           UserQueryRepository users) {
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
    }

    @Override
    public ResponseEntity<AdminSessionListResponse> adminListSessions(Long userId, String clientType,
                                                                      Boolean active, String after,
                                                                      Integer limit) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, SESSION_READ, "IAM_SESSION_COLLECTION", "sessions")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int pageSize = limit == null ? 50 : limit;
        var cursor = decode(after);
        var page = sessions.search(cursor.loginAt(), cursor.sessionId(), pageSize, userId, clientType, active);
        var items = page.stream().map(IamManagementSessionsController::toResponse).toList();
        String next = items.size() < pageSize ? null
                : encode(page.getLast().loginAt(), page.getLast().sessionId());
        return ResponseEntity.ok(new AdminSessionListResponse(items).nextAfter(next));
    }

    @Override
    public ResponseEntity<SessionResponse> getAdminSession(String sessionId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, SESSION_READ, "IAM_SESSION", sessionId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return sessions.findById(sessionId)
                .map(IamManagementSessionsController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<SessionResponse>> listUserSessions(Long userId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, SESSION_READ, "IAM_USER", userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (users.findById(userId).isEmpty()) return ResponseEntity.notFound().build();
        var items = sessions.findByUserId(userId).stream()
                .map(IamManagementSessionsController::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    static SessionResponse toResponse(AuthSession session) {
        var response = new SessionResponse(session.sessionId(), session.userId(), session.clientType(),
                Date.from(session.loginAt()), Date.from(session.lastSeenAt()), Date.from(session.expiresAt()))
                .clientInstance(session.clientInstance())
                .ipAddress(session.ipAddress())
                .userAgent(session.userAgent())
                .logoutAt(session.logoutAt() == null ? null : Date.from(session.logoutAt()))
                .revokeReason(session.revokeReason());
        if (session.logoutAt() != null || session.revoked()) {
            response.status(SessionResponse.StatusEnum.REVOKED);
        } else if (session.expiresAt().isBefore(Instant.now())) {
            response.status(SessionResponse.StatusEnum.EXPIRED);
        } else {
            response.status(SessionResponse.StatusEnum.ACTIVE);
        }
        return response;
    }

    private static Cursor decode(String after) {
        if (after == null || after.isBlank()) return new Cursor(null, null);
        int separator = after.indexOf(CURSOR_SEPARATOR);
        if (separator <= 0) throw new IllegalArgumentException("malformed session cursor");
        long epochMillis = Long.parseLong(after.substring(0, separator));
        String sessionId = after.substring(separator + 1);
        if (sessionId.isBlank()) throw new IllegalArgumentException("malformed session cursor");
        return new Cursor(Instant.ofEpochMilli(epochMillis), sessionId);
    }

    private static String encode(Instant loginAt, String sessionId) {
        return loginAt.toEpochMilli() + CURSOR_SEPARATOR + sessionId;
    }

    private record Cursor(Instant loginAt, String sessionId) {
    }
}
