package cloud.muer.web;

import cloud.muer.authorization.AuthorizationEngine;
import cloud.muer.core.model.IamUser;
import cloud.muer.core.model.Identity;
import cloud.muer.core.port.IdentityRepository;
import cloud.muer.core.port.UserQueryRepository;
import cloud.muer.web.api.ManagementIdentityApi;
import cloud.muer.web.api.ManagementUsersApi;
import cloud.muer.web.dto.IdentityResponse;
import cloud.muer.web.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static cloud.muer.web.WebSecurity.allowedRead;
import static cloud.muer.web.WebSecurity.currentPrincipal;

@RestController
public class IamManagementUsersController implements ManagementUsersApi, ManagementIdentityApi {
    private static final String USER_READ = "iam.admin.user.read";
    private static final String IDENTITY_READ = "iam.admin.identity.read";

    private final AuthorizationEngine authorization;
    private final UserQueryRepository users;
    private final IdentityRepository identities;

    public IamManagementUsersController(AuthorizationEngine authorization, UserQueryRepository users,
                                        IdentityRepository identities) {
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
    }

    @Override
    public ResponseEntity<UserResponse> getUser(Long userId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, USER_READ, "IAM_USER", userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return users.findById(userId)
                .map(IamManagementUsersController::userResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<IdentityResponse> getIdentity(String identityId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, IDENTITY_READ, "IAM_IDENTITY", identityId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return identities.findById(identityId)
                .map(IamManagementUsersController::identityResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static UserResponse userResponse(IamUser user) {
        return new UserResponse(user.userId(), user.username(), user.userType(), user.enabled(),
                user.authorizationVersion());
    }

    private static IdentityResponse identityResponse(Identity identity) {
        return new IdentityResponse(identity.identityId(), identity.userId(), identity.identityKey(),
                identity.domain(), identity.enabled());
    }
}
