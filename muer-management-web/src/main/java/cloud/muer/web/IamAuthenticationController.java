package cloud.muer.web;

import cloud.muer.authentication.AuthenticationService;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.session.TokenStore;
import cloud.muer.session.SessionService;
import cloud.muer.web.api.AuthenticationApi;
import cloud.muer.web.dto.LoginResponse;
import cloud.muer.web.dto.PrincipalResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

@RestController
public class IamAuthenticationController implements AuthenticationApi {
    private final AuthenticationService authentication;
    private final TokenStore tokens;
    private final SessionService sessions;

    public IamAuthenticationController(AuthenticationService authentication, TokenStore tokens,
                                       SessionService sessions) {
        this.authentication = authentication;
        this.tokens = tokens;
        this.sessions = sessions;
    }

    @Override
    public ResponseEntity<LoginResponse> login(cloud.muer.web.dto.LoginRequest request) {
        var servletRequest = currentServletRequest();
        var result = authentication.login(new cloud.muer.authentication.LoginRequest(
                request.getUsername(), request.getPassword(), request.getClientType(), request.getClientInstance(),
                bounded(servletRequest == null ? null : servletRequest.getRemoteAddr(), 64),
                header(servletRequest, HttpHeaders.USER_AGENT, 1024),
                header(servletRequest, "X-Client-Device-Type", 64),
                header(servletRequest, "X-Client-OS", 128),
                header(servletRequest, "X-Client-Browser", 128),
                header(servletRequest, "X-App-Version", 64),
                header(servletRequest, "X-Request-ID", 128)));
        return result
                .map(value -> ResponseEntity.ok(new LoginResponse(
                        value.accessToken(),
                        value.sessionId(),
                        Date.from(value.expiresAt()),
                        toResponse(value.principal()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public ResponseEntity<PrincipalResponse> getCurrentPrincipal() {
        var security = SecurityContextHolder.getContext().getAuthentication();
        if (security == null || !security.isAuthenticated() || !(security.getPrincipal() instanceof IamPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toResponse(principal));
    }

    @Override
    public ResponseEntity<Void> logout() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var record = tokens.resolve(token);
        if (record.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var resolved = record.orElseThrow();
        sessions.revoke(resolved.principal().userId(), resolved.sessionId(), "USER_REQUEST");
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private static PrincipalResponse toResponse(IamPrincipal principal) {
        return new PrincipalResponse(
                principal.userId(),
                principal.identityId(),
                principal.identityDomain(),
                principal.activeProfileId(),
                principal.templateVersionId(),
                principal.clientType(),
                principal.authorizationVersion());
    }

    private static jakarta.servlet.http.HttpServletRequest currentServletRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String header(jakarta.servlet.http.HttpServletRequest request, String name, int maxLength) {
        return request == null ? null : bounded(request.getHeader(name), maxLength);
    }

    private static String bounded(String value, int maxLength) {
        if (value == null) return null;
        var sanitized = new StringBuilder(Math.min(value.length(), maxLength));
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(maxLength)
                .forEach(sanitized::appendCodePoint);
        return sanitized.isEmpty() ? null : sanitized.toString();
    }
}
