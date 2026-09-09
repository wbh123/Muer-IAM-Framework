package cloud.muer.http;

import cloud.muer.authorization.AuthorizationRequest;
import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.authorization.AuthorizationProfileService;
import cloud.muer.authentication.AuthorizationProfileSwitchService;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ScopeAccess;
import cloud.muer.diagnostics.AuthorizationDiagnosticsService;
import cloud.muer.http.api.AuthorizationApi;
import cloud.muer.http.dto.AuthorizationDecisionResponse;
import cloud.muer.http.dto.AuthorizationEvaluationRequest;
import cloud.muer.http.dto.AuthorizationProfileResponse;
import cloud.muer.http.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Date;

/**
 * Authorization self-service endpoints. {@code evaluateAuthorization} is
 * <em>authenticated self diagnostics</em>: any valid {@link IamPrincipal} may
 * project their own authorization decision without any {@code iam.admin.*}
 * permission. It never accepts a userId/profileId to diagnose someone else.
 */
@RestController
public class IamAuthorizationController implements AuthorizationApi {
    private final AuthorizationDiagnosticsService diagnostics;
    private final AuthorizationProfileService profiles;
    private final AuthorizationProfileSwitchService profileSwitches;

    public IamAuthorizationController(AuthorizationDiagnosticsService diagnostics,
                                      AuthorizationProfileService profiles,
                                      AuthorizationProfileSwitchService profileSwitches) {
        this.diagnostics = diagnostics;
        this.profiles = profiles;
        this.profileSwitches = profileSwitches;
    }

    @Override
    public ResponseEntity<AuthorizationDecisionResponse> evaluateAuthorization(AuthorizationEvaluationRequest request) {
        var principal = currentPrincipal();
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var authorizationRequest = new AuthorizationRequest(
                request.getPermissionCode(),
                request.getDomain(),
                request.getClientType(),
                new ResourceDescriptor(request.getResourceType(), request.getResourceId(), List.of(), Map.of()),
                ScopeAccess.valueOf(request.getScopeAccess().getValue()));
        var decision = diagnostics.evaluate(principal, authorizationRequest);
        var steps = decision.steps().stream()
                .map(step -> new cloud.muer.http.dto.AuthorizationDecisionStep(
                        step.code(), step.passed(), step.reason()))
                .toList();
        return ResponseEntity.ok(new AuthorizationDecisionResponse(
                decision.allowed(), decision.decisionCode(), steps));
    }

    @Override
    public ResponseEntity<List<AuthorizationProfileResponse>> listMyAuthorizationProfiles() {
        var principal = currentPrincipal();
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(profiles.availableFor(principal).stream()
                .map(IamAuthorizationController::toResponse)
                .toList());
    }

    @Override
    public ResponseEntity<LoginResponse> switchAuthorizationProfile(Long profileId) {
        var principal = currentPrincipal();
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return profileSwitches.switchTo(principal, profileId)
                .map(result -> ResponseEntity.ok(new LoginResponse(
                        result.accessToken(), result.sessionId(), Date.from(result.expiresAt()),
                        toResponse(result.principal()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static IamPrincipal currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IamPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private static AuthorizationProfileResponse toResponse(AuthorizationProfile profile) {
        var clientTypes = profile.clientTypes().stream().sorted().toList();
        var scopes = profile.scopes().stream()
                .map(scope -> new cloud.muer.http.dto.ResourceScope(
                        scope.scopeType(), scope.scopeRefId(),
                        cloud.muer.http.dto.ResourceScope.AccessModeEnum.fromValue(scope.accessMode().name())))
                .toList();
        var response = new AuthorizationProfileResponse(
                profile.profileId(), profile.userId(), profile.profileName(), profile.templateVersionId(),
                clientTypes, profile.enabled(), profile.revoked(), scopes);
        if (profile.validFrom() != null) response.validFrom(Date.from(profile.validFrom()));
        if (profile.validUntil() != null) response.validUntil(Date.from(profile.validUntil()));
        return response;
    }

    private static cloud.muer.http.dto.PrincipalResponse toResponse(IamPrincipal principal) {
        return new cloud.muer.http.dto.PrincipalResponse(
                principal.userId(), principal.identityId(), principal.identityDomain(),
                principal.activeProfileId(), principal.templateVersionId(), principal.clientType(),
                principal.authorizationVersion());
    }
}
