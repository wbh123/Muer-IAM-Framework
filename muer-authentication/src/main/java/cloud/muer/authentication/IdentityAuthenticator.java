package cloud.muer.authentication;

import cloud.muer.core.model.IamPrincipal;

import java.util.Optional;

@FunctionalInterface
public interface IdentityAuthenticator {
    Optional<IamPrincipal> authenticate(LoginRequest request);
}
