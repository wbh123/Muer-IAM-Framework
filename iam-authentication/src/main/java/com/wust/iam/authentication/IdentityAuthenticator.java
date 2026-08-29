package com.wust.iam.authentication;

import com.wust.iam.core.model.IamPrincipal;

import java.util.Optional;

@FunctionalInterface
public interface IdentityAuthenticator {
    Optional<IamPrincipal> authenticate(LoginRequest request);
}
