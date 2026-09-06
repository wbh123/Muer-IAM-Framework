package io.github.muer.authentication;

import io.github.muer.core.model.IamPrincipal;

import java.util.Optional;

@FunctionalInterface
public interface IdentityAuthenticator {
    Optional<IamPrincipal> authenticate(LoginRequest request);
}
