package io.github.iamstarter.authorization;

import io.github.iamstarter.core.model.ResourceDescriptor;
import io.github.iamstarter.core.model.ScopeAccess;

import java.util.Objects;

public record AuthorizationRequest(String permissionCode, String domain, String clientType,
                                   ResourceDescriptor resource, ScopeAccess scopeAccess) {
    public AuthorizationRequest {
        permissionCode = required(permissionCode, "permissionCode");
        domain = required(domain, "domain");
        clientType = required(clientType, "clientType");
        resource = Objects.requireNonNull(resource, "resource must not be null");
        scopeAccess = Objects.requireNonNull(scopeAccess, "scopeAccess must not be null");
    }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
