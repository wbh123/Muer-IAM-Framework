package io.github.iamstarter.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decodes the string-to-string JSON objects written by {@link JsonObjectEncoder}.
 * The writer is the only producer of these columns, so this decoder only needs to
 * support flat objects whose values are strings.
 */
final class JsonObjectDecoder {
    Map<String, String> decode(String json) {
        if (json == null || json.isBlank()) return Map.of();
        String body = json.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new IllegalArgumentException("audit payload is not a JSON object");
        }
        var result = new LinkedHashMap<String, String>();
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) return Map.copyOf(result);
        int index = 0;
        while (index < body.length()) {
            index = skipWhitespace(body, index);
            if (index >= body.length()) break;
            String key = readString(body, index);
            index = skipWhitespace(body, index + key.length());
            if (index >= body.length() || body.charAt(index) != ':') {
                throw new IllegalArgumentException("malformed audit payload");
            }
            index = skipWhitespace(body, index + 1);
            String value = readString(body, index);
            result.put(key, value);
            index = skipWhitespace(body, index + value.length());
            if (index < body.length()) {
                char separator = body.charAt(index);
                if (separator == ',') index++;
                else if (separator == '}') break;
                else throw new IllegalArgumentException("malformed audit payload");
            }
        }
        return Map.copyOf(result);
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return index;
    }

    /**
     * Reads one quoted string starting at the opening quote and returns its
     * unescaped content.
     */
    private static String readString(String value, int index) {
        if (index >= value.length() || value.charAt(index) != '"') {
            throw new IllegalArgumentException("malformed audit payload");
        }
        var out = new StringBuilder();
        int cursor = index + 1;
        while (cursor < value.length()) {
            char current = value.charAt(cursor);
            if (current == '"') return out.toString();
            if (current == '\\') {
                cursor++;
                if (cursor >= value.length()) throw new IllegalArgumentException("malformed audit payload");
                char escaped = value.charAt(cursor);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (cursor + 4 >= value.length()) throw new IllegalArgumentException("malformed audit payload");
                        String hex = value.substring(cursor + 1, cursor + 5);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException exception) {
                            throw new IllegalArgumentException("malformed audit payload");
                        }
                        cursor += 4;
                    }
                    default -> throw new IllegalArgumentException("malformed audit payload");
                }
            } else {
                out.append(current);
            }
            cursor++;
        }
        throw new IllegalArgumentException("malformed audit payload");
    }
}
