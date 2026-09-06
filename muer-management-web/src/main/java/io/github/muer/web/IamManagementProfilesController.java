package io.github.iamstarter.web;

import io.github.iamstarter.authorization.AuthorizationEngine;
import io.github.iamstarter.authorization.AuthorizationProfile;
import io.github.iamstarter.authorization.AuthorizationProfileQueryRepository;
import io.github.iamstarter.authorization.AuthorizationProfileRepository;
import io.github.iamstarter.authorization.AuthorizationProfileService;
import io.github.iamstarter.core.port.UserQueryRepository;
import io.github.iamstarter.web.api.ManagementProfilesApi;
import io.github.iamstarter.web.dto.AuthorizationProfileResponse;
import io.github.iamstarter.web.dto.ProfileListResponse;
import io.github.iamstarter.web.dto.ResourceScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.NoSuchElementException;

import static io.github.iamstarter.web.WebSecurity.allowedRead;
import static io.github.iamstarter.web.WebSecurity.currentPrincipal;

@RestController
public class IamManagementProfilesController implements ManagementProfilesApi {
    private static final String PROFILE_READ = "iam.admin.profile.read";
    private static final String SCOPE_READ = "iam.admin.scope.read";

    private final AuthorizationEngine authorization;
    private final AuthorizationProfileQueryRepository query;
    private final AuthorizationProfileRepository profiles;
    private final UserQueryRepository users;

    public IamManagementProfilesController(AuthorizationEngine authorization,
                                           AuthorizationProfileQueryRepository query,
                                           AuthorizationProfileRepository profiles,
                                           UserQueryRepository users) {
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.profiles = Objects.requireNonNull(profiles, "profiles must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
    }

    @Override
    public ResponseEntity<ProfileListResponse> listAuthorizationProfiles(Long afterProfileId, Integer limit,
                                                                         Long userId, Long templateVersionId,
                                                                         Boolean enabled, Boolean revoked,
                                                                         String clientType) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, PROFILE_READ,
                "IAM_AUTHORIZATION_PROFILE_COLLECTION", "profiles")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int pageSize = limit == null ? 50 : limit;
        var page = query.search(afterProfileId == null ? 0L : afterProfileId, pageSize, userId,
                templateVersionId, enabled, revoked, clientType);
        var items = page.stream().map(IamManagementProfilesController::toResponse).toList();
        Long next = items.size() < pageSize ? null : items.getLast().getProfileId();
        return ResponseEntity.ok(new ProfileListResponse(items).nextAfterProfileId(next));
    }

    @Override
    public ResponseEntity<AuthorizationProfileResponse> getAuthorizationProfile(Long profileId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, PROFILE_READ,
                "IAM_AUTHORIZATION_PROFILE", profileId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(toResponse(profiles.require(profileId)));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<List<ResourceScope>> getAuthorizationProfileScopes(Long profileId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, SCOPE_READ,
                "IAM_AUTHORIZATION_PROFILE", profileId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            var scopes = profiles.require(profileId).scopes().stream()
                    .map(IamManagementProfilesController::toScope)
                    .toList();
            return ResponseEntity.ok(scopes);
        } catch (NoSuchElementException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<List<AuthorizationProfileResponse>> listUserAuthorizationProfiles(Long userId) {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!allowedRead(authorization, principal, PROFILE_READ, "IAM_USER", userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (users.findById(userId).isEmpty()) return ResponseEntity.notFound().build();
        var owned = profiles.findByUserId(userId).stream()
                .map(IamManagementProfilesController::toResponse)
                .toList();
        return ResponseEntity.ok(owned);
    }

    private static AuthorizationProfileResponse toResponse(AuthorizationProfile profile) {
        var response = new AuthorizationProfileResponse(
                profile.profileId(), profile.userId(), profile.profileName(), profile.templateVersionId(),
                profile.clientTypes().stream().sorted().toList(), profile.enabled(), profile.revoked(),
                profile.scopes().stream().map(IamManagementProfilesController::toScope).toList());
        if (profile.validFrom() != null) response.validFrom(Date.from(profile.validFrom()));
        if (profile.validUntil() != null) response.validUntil(Date.from(profile.validUntil()));
        return response;
    }

    private static ResourceScope toScope(io.github.iamstarter.core.model.ResourceScope scope) {
        return new ResourceScope(scope.scopeType(), scope.scopeRefId(),
                ResourceScope.AccessModeEnum.fromValue(scope.accessMode().name()));
    }
}
