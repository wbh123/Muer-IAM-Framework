package cloud.muer.authorization;

import java.util.Objects;

/** Stable application-facing declaration of one managed permission. */
public record PermissionDefinition(String code, String displayName, String description) {
    private static final int CODE_MAX_LENGTH = 191;
    private static final int DISPLAY_NAME_MAX_LENGTH = 191;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    public PermissionDefinition {
        code = required(code, "code", CODE_MAX_LENGTH);
        displayName = required(displayName, "displayName", DISPLAY_NAME_MAX_LENGTH);
        description = optional(description, "description", DESCRIPTION_MAX_LENGTH);
    }

    private static String required(String value, String name, int maxLength) {
        String normalized = optional(value, name, maxLength);
        if (normalized == null) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    private static String optional(String value, String name, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must not contain control characters");
        }
        return normalized;
    }
}
