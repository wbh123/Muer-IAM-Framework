package io.github.muer.autoconfigure.web;

/** Stable failure categories emitted by declarative MVC IAM authorization. */
public enum IamAuthorizationFailure {
    UNAUTHENTICATED("IAM_UNAUTHENTICATED"),
    ACCESS_DENIED("IAM_ACCESS_DENIED"),
    RESOURCE_NOT_FOUND("IAM_RESOURCE_NOT_FOUND"),
    RESOURCE_RESOLUTION_UNAVAILABLE("IAM_RESOURCE_RESOLUTION_UNAVAILABLE");

    private final String code;

    IamAuthorizationFailure(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
