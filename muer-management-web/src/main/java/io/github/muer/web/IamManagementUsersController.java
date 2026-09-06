package io.github.muer.web;

import io.github.muer.authorization.AuthorizationEngine;
import io.github.muer.core.model.IamUser;
import io.github.muer.core.model.Identity;
import io.github.muer.core.port.IdentityRepository;
import io.github.muer.core.port.UserQueryRepository;
import io.github.muer.web.api.ManagementIdentityApi;
import io.github.muer.web.api.ManagementUsersApi;
import io.github.muer.web.dto.IdentityResponse;
import io.github.muer.web.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static io.github.muer.web.WebSecurity.allowedRead;
import static io.github.muer.web.WebSecurity.currentPrincipal;

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
