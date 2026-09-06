package io.github.muer.core.model;

import java.util.Objects;

public record Permission(String permissionCode, String domain, boolean enabled) {
    public Permission {
        permissionCode = required(permissionCode, "permissionCode");
        domain = required(domain, "domain");
    }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
