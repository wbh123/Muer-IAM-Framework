package io.github.muer.core.model;

import java.util.Objects;

/**
 * Framework-neutral account subject used by authentication and authorization.
 */
public record IamUser(
        long userId,
        String username,
        String userType,
        boolean enabled,
        long authorizationVersion) {

    public IamUser {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        username = requireText(username, "username");
        userType = requireText(userType, "userType");
        if (authorizationVersion < 0) {
            throw new IllegalArgumentException("authorizationVersion must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
