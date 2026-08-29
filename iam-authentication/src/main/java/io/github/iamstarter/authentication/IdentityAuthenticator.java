package io.github.iamstarter.authentication;

import io.github.iamstarter.core.model.IamPrincipal;

import java.util.Optional;

@FunctionalInterface
public interface IdentityAuthenticator {
    Optional<IamPrincipal> authenticate(LoginRequest request);
}
