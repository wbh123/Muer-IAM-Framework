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
        return new Parser(body).parseObject();
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
            this.index = 1;
        }

        private Map<String, String> parseObject() {
            var result = new LinkedHashMap<String, String>();
            skipWhitespace();
            if (peek() == '}') return Map.copyOf(result);
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw new IllegalArgumentException("malformed audit payload");
                String key = readString();
                skipWhitespace();
                if (peek() != ':') throw new IllegalArgumentException("malformed audit payload");
                index++;
                skipWhitespace();
                if (peek() != '"') throw new IllegalArgumentException("malformed audit payload");
                String value = readString();
                result.put(key, value);
                skipWhitespace();
                char separator = next();
                if (separator == '}') return Map.copyOf(result);
                if (separator != ',') throw new IllegalArgumentException("malformed audit payload");
            }
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private char peek() {
            if (index >= source.length()) throw new IllegalArgumentException("malformed audit payload");
            return source.charAt(index);
        }

        private char next() {
            if (index >= source.length()) throw new IllegalArgumentException("malformed audit payload");
            return source.charAt(index++);
        }

        private String readString() {
            if (peek() != '"') throw new IllegalArgumentException("malformed audit payload");
            index++;
            var out = new StringBuilder();
            while (index < source.length()) {
                char current = source.charAt(index);
                if (current == '"') {
                    index++;
                    return out.toString();
                }
                if (current == '\\') {
                    index++;
                    if (index >= source.length()) throw new IllegalArgumentException("malformed audit payload");
                    char escaped = source.charAt(index);
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
                            if (index + 4 >= source.length()) throw new IllegalArgumentException("malformed audit payload");
                            String hex = source.substring(index + 1, index + 5);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException exception) {
                                throw new IllegalArgumentException("malformed audit payload");
                            }
                            index += 4;
                        }
                        default -> throw new IllegalArgumentException("malformed audit payload");
                    }
                } else {
                    out.append(current);
                }
                index++;
            }
            throw new IllegalArgumentException("malformed audit payload");
        }
    }
}
