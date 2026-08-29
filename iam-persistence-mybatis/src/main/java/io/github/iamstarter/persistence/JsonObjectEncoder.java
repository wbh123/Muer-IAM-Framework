package io.github.iamstarter.persistence;

import java.util.Map;
import java.util.stream.Collectors;

final class JsonObjectEncoder {
    String encode(Map<String, String> values) {
        if (values == null || values.isEmpty()) return "{}";
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> quoted(entry.getKey()) + ":" + quoted(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String quoted(String value) {
        var escaped = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(codePoint -> appendEscaped(escaped, codePoint));
        return escaped.append('"').toString();
    }

    private void appendEscaped(StringBuilder target, int codePoint) {
        switch (codePoint) {
            case '"' -> target.append("\\\"");
            case '\\' -> target.append("\\\\");
            case '\b' -> target.append("\\b");
            case '\f' -> target.append("\\f");
            case '\n' -> target.append("\\n");
            case '\r' -> target.append("\\r");
            case '\t' -> target.append("\\t");
            default -> {
                if (codePoint < 0x20) target.append(String.format("\\u%04x", codePoint));
                else target.appendCodePoint(codePoint);
            }
        }
    }
}
