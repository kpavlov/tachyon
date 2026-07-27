/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Shared value coercion, copying, and serialization for {@link DefaultJsonObject} and {@link
 * DefaultJsonArray}. Callers pass a {@code location} label (e.g. {@code "property 'city'"} or
 * {@code "element [2]"}) that is embedded in error messages.
 */
final class JsonValues {

    private JsonValues() {}

    static <T> Optional<T> optionalValue(@Nullable Object value, Class<T> type, String jsonType, String location) {
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw wrongType(location, jsonType, value);
        }
        return Optional.of(type.cast(value));
    }

    static Optional<BigDecimal> decimalOpt(@Nullable Object value, String location) {
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Number number)) {
            throw wrongType(location, "number", value);
        }
        return Optional.of(decimal(number));
    }

    static OptionalInt intOpt(@Nullable Object value, String location) {
        var decimal = decimalOpt(value, location);
        if (decimal.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(decimal.orElseThrow().intValueExact());
        } catch (ArithmeticException e) {
            throw invalidNumber(location, "int", e);
        }
    }

    static OptionalLong longOpt(@Nullable Object value, String location) {
        var decimal = decimalOpt(value, location);
        if (decimal.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(decimal.orElseThrow().longValueExact());
        } catch (ArithmeticException e) {
            throw invalidNumber(location, "long", e);
        }
    }

    static OptionalDouble doubleOpt(@Nullable Object value, String location) {
        var decimal = decimalOpt(value, location);
        if (decimal.isEmpty()) {
            return OptionalDouble.empty();
        }
        var result = decimal.orElseThrow().doubleValue();
        if (!Double.isFinite(result)) {
            throw invalidNumber(location, "finite double", null);
        }
        return OptionalDouble.of(result);
    }

    static JsonObject object(Object value, String location) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            var object = (Map<String, Object>) map;
            return DefaultJsonObject.fromImmutableValues(object, null);
        }
        throw wrongType(location, "object", value);
    }

    static JsonArray array(Object value, String location) {
        if (value instanceof List<?> list) {
            return new DefaultJsonArray(list);
        }
        throw wrongType(location, "array", value);
    }

    static BigDecimal decimal(Number value) {
        return switch (value) {
            case BigDecimal decimal -> decimal;
            case BigInteger integer -> new BigDecimal(integer);
            case Byte number -> BigDecimal.valueOf(number.longValue());
            case Short number -> BigDecimal.valueOf(number.longValue());
            case Integer number -> BigDecimal.valueOf(number.longValue());
            case Long number -> BigDecimal.valueOf(number);
            case Float number -> new BigDecimal(Float.toString(number));
            case Double number -> BigDecimal.valueOf(number);
            default ->
                throw new IllegalArgumentException(
                        "Unsupported JSON number type " + value.getClass().getName());
        };
    }

    static @Nullable Object copyValue(@Nullable Object value, String path) {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof BigDecimal
                || value instanceof BigInteger
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return value;
        }
        switch (value) {
            case Float number -> {
                if (!Float.isFinite(number)) {
                    throw invalidValue(path, "non-finite number");
                }
                return number;
            }
            case Double number -> {
                if (!Double.isFinite(number)) {
                    throw invalidValue(path, "non-finite number");
                }
                return number;
            }
            case Map<?, ?> map -> {
                var copy = new LinkedHashMap<String, Object>(map.size());
                map.forEach((key, nested) -> {
                    if (!(key instanceof String name)) {
                        throw invalidValue(path, "object property name is not a string");
                    }
                    copy.put(name, copyValue(nested, path + "." + name));
                });
                return Collections.unmodifiableMap(copy);
            }
            case List<?> list -> {
                var copy = new ArrayList<>(list.size());
                for (var index = 0; index < list.size(); index++) {
                    copy.add(copyValue(list.get(index), path + "[" + index + "]"));
                }
                return Collections.unmodifiableList(copy);
            }
            case JsonObject object -> {
                return copyValue(object.asMap(), path);
            }
            case JsonArray array -> {
                return copyValue(array.asList(), path);
            }
            default -> {}
        }
        throw invalidValue(path, "unsupported value type " + value.getClass().getName());
    }

    static IllegalArgumentException wrongType(String location, String expected, Object actual) {
        return new IllegalArgumentException("JSON %s must be %s, but was %s"
                .formatted(location, expected, actual.getClass().getSimpleName()));
    }

    static IllegalArgumentException invalidNumber(
            String location, String expected, @Nullable ArithmeticException cause) {
        return new IllegalArgumentException("JSON %s is not an exact %s".formatted(location, expected), cause);
    }

    private static IllegalArgumentException invalidValue(String path, String reason) {
        return new IllegalArgumentException("Invalid JSON value at '%s': %s".formatted(path, reason));
    }

    static String writeJson(Object value) {
        var json = new StringBuilder();
        appendJson(json, value);
        return json.toString();
    }

    private static void appendJson(StringBuilder json, @Nullable Object value) {
        switch (value) {
            case null -> json.append("null");
            case String text -> appendString(json, text);
            case Boolean bool -> json.append(bool);
            case Number number -> json.append(number);
            case Map<?, ?> map -> {
                json.append('{');
                var first = true;
                for (var entry : map.entrySet()) {
                    if (!first) {
                        json.append(',');
                    }
                    appendString(json, (String) entry.getKey());
                    json.append(':');
                    appendJson(json, entry.getValue());
                    first = false;
                }
                json.append('}');
            }
            case List<?> list -> {
                json.append('[');
                for (var index = 0; index < list.size(); index++) {
                    if (index > 0) {
                        json.append(',');
                    }
                    appendJson(json, list.get(index));
                }
                json.append(']');
            }
            default ->
                throw new IllegalArgumentException(
                        "Unsupported JSON value type " + value.getClass().getName());
        }
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u");
                        json.append(Character.forDigit(character >>> 12, 16));
                        json.append(Character.forDigit((character >>> 8) & 0xf, 16));
                        json.append(Character.forDigit((character >>> 4) & 0xf, 16));
                        json.append(Character.forDigit(character & 0xf, 16));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
