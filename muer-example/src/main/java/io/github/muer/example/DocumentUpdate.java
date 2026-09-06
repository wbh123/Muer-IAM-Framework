package io.github.muer.example;

/** A minimal host-owned payload; IAM only decides whether the operation is allowed. */
public record DocumentUpdate(String status) {
    public DocumentUpdate {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status must not be blank");
    }
}
