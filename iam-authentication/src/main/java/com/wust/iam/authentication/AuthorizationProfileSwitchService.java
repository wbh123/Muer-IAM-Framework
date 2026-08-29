package com.wust.iam.authentication;

import com.wust.iam.authorization.AuthorizationProfileService;
import com.wust.iam.core.model.IamPrincipal;

import java.util.Objects;
import java.util.Optional;

public final class AuthorizationProfileSwitchService {
    private final AuthorizationProfileService profiles;
    private final AuthenticationService authentication;

    public AuthorizationProfileSwitchService(AuthorizationProfileService profiles,
                                             AuthenticationService authentication) {
        this.profiles = Objects.requireNonNull(profiles);
        this.authentication = Objects.requireNonNull(authentication);
    }

    public Optional<AuthenticationResult> switchTo(IamPrincipal current, long profileId) {
        if (profileId <= 0) return Optional.empty();
        return profiles.availableFor(current).stream()
                .filter(profile -> profile.profileId() == profileId)
                .findFirst()
                .map(profile -> authentication.issueForProfile(current, profile));
    }
}
