package cloud.muer.authorization;

import cloud.muer.core.model.IamPrincipal;

@FunctionalInterface
public interface AuthorizationPolicy {
    AuthorizationPolicyResult evaluate(IamPrincipal principal, AuthorizationRequest request);
}
