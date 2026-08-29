package com.wust.iam.authorization;

import java.util.Objects;

public record AuthorizationPolicyResult(boolean allowed, String code, String reason) {
    public AuthorizationPolicyResult {
        code = required(code, "code");
        reason = required(reason, "reason");
    }

    public static AuthorizationPolicyResult allow(String code, String reason) {
        return new AuthorizationPolicyResult(true, code, reason);
    }

    public static AuthorizationPolicyResult deny(String code, String reason) {
        return new AuthorizationPolicyResult(false, code, reason);
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
