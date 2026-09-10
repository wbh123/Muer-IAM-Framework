package cloud.muer.http;

import cloud.muer.authorization.AuthorizationProfile;
import cloud.muer.authorization.AuthorizationProfileRepository;
import cloud.muer.authorization.PermissionTemplateVersionRepository;
import cloud.muer.core.model.IamPrincipal;
import cloud.muer.http.api.CapabilitiesApi;
import cloud.muer.http.dto.PrincipalCapabilitiesResponse;
import cloud.muer.http.dto.PrincipalResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cloud.muer.http.WebSecurity.currentPrincipal;

/**
 * Returns only capabilities that belong to the current principal itself. No other
 * subject data is exposed and no administration permission is required.
 */
@RestController
public class IamCapabilitiesController implements CapabilitiesApi {
    private final AuthorizationProfileRepository profiles;
    private final PermissionTemplateVersionRepository versions;

    public IamCapabilitiesController(AuthorizationProfileRepository profiles,
                                     PermissionTemplateVersionRepository versions) {
        this.profiles = Objects.requireNonNull(profiles, "profiles must not be null");
        this.versions = Objects.requireNonNull(versions, "versions must not be null");
    }

    @Override
    public ResponseEntity<PrincipalCapabilitiesResponse> getCurrentCapabilities() {
        var principal = currentPrincipal();
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Set<String> permissions = Set.of();
        List<cloud.muer.core.model.ResourceScope> scopes = List.of();
        Long activeProfileId = principal.activeProfileId();
        Long templateVersionId = principal.templateVersionId();
        try {
            AuthorizationProfile profile = profiles.require(principal.activeProfileId());
            permissions = versions.require(profile.templateVersionId()).permissions();
            scopes = profile.scopes();
            templateVersionId = profile.templateVersionId();
        } catch (RuntimeException unavailable) {
            // A stale or removed active profile yields an empty capability set rather
            // than leaking anything beyond the principal metadata in the token.
            permissions = Set.of();
            scopes = List.of();
        }

        var principalResponse = new PrincipalResponse(
                principal.userId(), principal.identityId(), principal.identityDomain(),
                activeProfileId, templateVersionId, principal.clientType(), principal.authorizationVersion());
        var scopeResponses = scopes.stream()
                .map(scope -> new cloud.muer.http.dto.ResourceScope(
                        scope.scopeType(), scope.scopeRefId(),
                        cloud.muer.http.dto.ResourceScope.AccessModeEnum.fromValue(scope.accessMode().name())))
                .toList();
        return ResponseEntity.ok(new PrincipalCapabilitiesResponse(
                principalResponse, activeProfileId, templateVersionId,
                Set.copyOf(permissions), scopeResponses));
    }
}
