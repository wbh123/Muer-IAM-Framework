package io.github.iamstarter.web;

import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationProfile;
import io.github.iamstarter.authorization.AuthorizationProfileService;
import io.github.iamstarter.authorization.AuthorizationRequest;
import io.github.iamstarter.authorization.AuthorizationVersionService;
import io.github.iamstarter.authorization.PermissionTemplateService;
import io.github.iamstarter.authorization.PermissionTemplateVersion;
import io.github.iamstarter.authorization.TemplateVersionStatus;
import io.github.iamstarter.core.model.IamPrincipal;
import io.github.iamstarter.core.model.ResourceDescriptor;
import io.github.iamstarter.core.model.ResourceScope;
import io.github.iamstarter.core.model.ScopeAccess;
import io.github.iamstarter.web.api.AdministrationApi;
import io.github.iamstarter.web.dto.AuthorizationProfileRequest;
import io.github.iamstarter.web.dto.IncrementAuthorizationVersion200Response;
import io.github.iamstarter.web.dto.PermissionTemplateVersionRequest;
import io.github.iamstarter.web.dto.RevokeRequest;
import io.github.iamstarter.web.dto.ScopeReplacementRequest;
import io.github.iamstarter.session.SessionService;
import io.github.iamstarter.authentication.AccountGovernanceService;
import io.github.iamstarter.core.model.IamUser;
import io.github.iamstarter.core.model.Identity;
import io.github.iamstarter.web.dto.UserRequest;
import io.github.iamstarter.web.dto.UserResponse;
import io.github.iamstarter.web.dto.UserListResponse;
import io.github.iamstarter.web.dto.IdentityRequest;
import io.github.iamstarter.web.dto.IdentityResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class IamAdministrationController implements AdministrationApi {
    private final AuthorizationEngine authorization;
    private final PermissionTemplateService templates;
    private final AuthorizationProfileService profiles;
    private final AuthorizationVersionService versions;
    private final SessionService sessions;
    private final AccountGovernanceService accounts;

    public IamAdministrationController(AuthorizationEngine authorization,
                                       PermissionTemplateService templates,
                                       AuthorizationProfileService profiles,
                                       AuthorizationVersionService versions) {
        this(authorization, templates, profiles, versions, null, null);
    }

    public IamAdministrationController(AuthorizationEngine authorization,
                                       PermissionTemplateService templates,
                                       AuthorizationProfileService profiles,
                                       AuthorizationVersionService versions,
                                       SessionService sessions) {
        this(authorization, templates, profiles, versions, sessions, null);
    }

    public IamAdministrationController(AuthorizationEngine authorization,
                                       PermissionTemplateService templates,
                                       AuthorizationProfileService profiles,
                                       AuthorizationVersionService versions,
                                       SessionService sessions,
                                       AccountGovernanceService accounts) {
        this.authorization = authorization;
        this.templates = templates;
        this.profiles = profiles;
        this.versions = versions;
        this.sessions = sessions;
        this.accounts = accounts;
    }

    @Override
    public ResponseEntity<IncrementAuthorizationVersion200Response> incrementAuthorizationVersion(Long userId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.authorization-version.increment", "IAM_USER", userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(new IncrementAuthorizationVersion200Response(versions.increment(userId)));
    }

    @Override
    public ResponseEntity<Void> saveAuthorizationProfile(Long profileId, AuthorizationProfileRequest request) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.profile.write", "IAM_AUTHORIZATION_PROFILE", profileId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!profileId.equals(request.getProfileId())) {
            return ResponseEntity.badRequest().build();
        }
        var scopes = request.getScopes().stream()
                .map(scope -> new ResourceScope(
                        scope.getScopeType(), scope.getScopeRefId(), ScopeAccess.valueOf(scope.getAccessMode().getValue())))
                .toList();
        profiles.replace(new AuthorizationProfile(
                profileId,
                request.getUserId(),
                request.getProfileName(),
                request.getTemplateVersionId(),
                Set.copyOf(request.getClientTypes()),
                request.getEnabled(),
                request.getRevoked(),
                request.getValidFrom() == null ? null : request.getValidFrom().toInstant(),
                request.getValidUntil() == null ? null : request.getValidUntil().toInstant(),
                scopes));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> replaceAuthorizationProfileScopes(Long profileId,
                                                                  ScopeReplacementRequest request) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.scope.write", "IAM_AUTHORIZATION_PROFILE", profileId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        profiles.replaceScopes(profileId, request.getScopes().stream()
                .map(scope -> new ResourceScope(scope.getScopeType(), scope.getScopeRefId(),
                        ScopeAccess.valueOf(scope.getAccessMode().getValue())))
                .toList());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> savePermissionTemplateVersion(Long versionId,
                                                              PermissionTemplateVersionRequest request) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.template.write", "IAM_PERMISSION_TEMPLATE_VERSION",
                versionId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        templates.replace(new PermissionTemplateVersion(
                versionId,
                request.getTemplateId(),
                request.getVersionNumber(),
                TemplateVersionStatus.valueOf(request.getStatus().getValue()),
                request.getPermissions()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> forceRevokeSession(String sessionId, RevokeRequest request) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.session.revoke", "IAM_SESSION", sessionId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        sessions.forceRevoke(sessionId, revokeReason(request));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> forceRevokeUserSessions(Long userId, RevokeRequest request) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.session.revoke-user", "IAM_USER", userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        sessions.forceRevokeUser(userId, revokeReason(request));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserListResponse> listUsers(Long afterUserId, Integer limit,
                                                      String username, String userType, Boolean enabled) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.user.read", "IAM_USER_COLLECTION", "users")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean filtered = username != null || userType != null || enabled != null;
        var values = filtered
                ? accounts.listUsers(afterUserId, limit, username, userType, enabled)
                : accounts.listUsers(afterUserId, limit);
        var items = values.stream().map(IamAdministrationController::userResponse).toList();
        Long next = values.size() < limit ? null : values.getLast().userId();
        return ResponseEntity.ok(new UserListResponse(items).nextAfterUserId(next));
    }

    @Override
    public ResponseEntity<Void> saveUser(Long userId, UserRequest request) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.user.write", "IAM_USER", userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        accounts.saveUser(userId, request.getUsername(), request.getUserType(), request.getEnabled());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<IdentityResponse>> listUserIdentities(Long userId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.identity.read", "IAM_USER", userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(accounts.listIdentities(userId).stream()
                .map(IamAdministrationController::identityResponse).toList());
    }

    @Override
    public ResponseEntity<Void> saveIdentity(String identityId, IdentityRequest request) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!isAllowed(principal, "iam.admin.identity.write", "IAM_IDENTITY", identityId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        accounts.saveIdentity(new Identity(identityId, request.getUserId(), request.getIdentityKey(),
                request.getDomain(), request.getEnabled()));
        return ResponseEntity.noContent().build();
    }

    private static UserResponse userResponse(IamUser user) {
        return new UserResponse(user.userId(), user.username(), user.userType(), user.enabled(),
                user.authorizationVersion());
    }

    private static IdentityResponse identityResponse(Identity identity) {
        return new IdentityResponse(identity.identityId(), identity.userId(), identity.identityKey(),
                identity.domain(), identity.enabled());
    }

    private static String revokeReason(RevokeRequest request) {
        return request == null || request.getReason() == null ? "ADMIN_ACTION" : request.getReason();
    }

    private boolean isAllowed(IamPrincipal principal, String permissionCode, String resourceType,
                              String resourceId) {
        var request = new AuthorizationRequest(
                permissionCode,
                principal.identityDomain(),
                principal.clientType(),
                new ResourceDescriptor(resourceType, resourceId, List.of(), Map.of()),
                ScopeAccess.WRITE);
        return authorization.decide(principal, request).allowed();
    }

    private static IamPrincipal currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IamPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
