package com.wust.iam.authorization;

import com.wust.iam.core.model.IamPrincipal;

@FunctionalInterface
public interface AuthorizationPolicy {
    AuthorizationPolicyResult evaluate(IamPrincipal principal, AuthorizationRequest request);
}
