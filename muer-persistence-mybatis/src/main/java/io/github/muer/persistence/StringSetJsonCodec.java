package io.github.muer.persistence;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class StringSetJsonCodec {
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]+");

    String encode(Set<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        return values.stream().sorted().map(this::quoted).collect(Collectors.joining(",", "[", "]"));
    }

    Set<String> decode(String json) {
        if (json == null || json.equals("[]")) return Set.of();
        if (!json.startsWith("[\"") || !json.endsWith("\"]")) {
            throw new IllegalStateException("invalid client type JSON");
        }
        var values = Arrays.stream(json.substring(2, json.length() - 2).split("\"\\s*,\\s*\"", -1))
                .peek(this::requireSafe)
                .collect(Collectors.toCollection(TreeSet::new));
        return Set.copyOf(values);
    }

    private String quoted(String value) {
        requireSafe(value);
        return "\"" + value + "\"";
    }

    private void requireSafe(String value) {
        if (value == null || !SAFE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("client type must be a stable identifier");
        }
    }
}
