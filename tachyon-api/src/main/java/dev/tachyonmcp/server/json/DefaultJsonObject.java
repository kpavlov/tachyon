/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import org.jspecify.annotations.Nullable;

final class DefaultJsonObject implements JsonObject {

    static final JsonObject EMPTY = new DefaultJsonObject(Map.of());

    private final Map<String, Object> values;
    private final String json;

    DefaultJsonObject(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        var copy = new LinkedHashMap<String, Object>(values.size());
        values.forEach((name, value) -> {
            if (name == null) {
                throw new IllegalArgumentException("JSON object property name must not be null");
            }
            copy.put(name, copyValue(value, name));
        });
        this.values = Collections.unmodifiableMap(copy);
        this.json = writeJson(this.values);
    }

    @Override
    public String json() {
        return json;
    }

    @Override
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    @Override
    public Optional<JsonObject> objectOpt(String name) {
        var value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            var object = (Map<String, ?>) map;
            return Optional.of(new DefaultJsonObject(object));
        }
        throw wrongType(name, "object", value);
    }

    @Override
    public Optional<String> stringOpt(String name) {
        return optionalValue(name, String.class, "string");
    }

    @Override
    public Optional<Boolean> boolOpt(String name) {
        return optionalValue(name, Boolean.class, "boolean");
    }

    @Override
    public Optional<BigDecimal> decimalOpt(String name) {
        var value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Number number)) {
            throw wrongType(name, "number", value);
        }
        return Optional.of(decimal(number));
    }

    @Override
    public OptionalInt intOpt(String name) {
        var decimal = decimalOpt(name);
        if (decimal.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(decimal.orElseThrow().intValueExact());
        } catch (ArithmeticException e) {
            throw invalidNumber(name, "int", e);
        }
    }

    @Override
    public OptionalLong longOpt(String name) {
        var decimal = decimalOpt(name);
        if (decimal.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(decimal.orElseThrow().longValueExact());
        } catch (ArithmeticException e) {
            throw invalidNumber(name, "long", e);
        }
    }

    @Override
    public OptionalDouble doubleOpt(String name) {
        var decimal = decimalOpt(name);
        if (decimal.isEmpty()) {
            return OptionalDouble.empty();
        }
        var value = decimal.orElseThrow().doubleValue();
        if (!Double.isFinite(value)) {
            throw invalidNumber(name, "finite double", null);
        }
        return OptionalDouble.of(value);
    }

    @Override
    public Map<String, Object> asMap() {
        return values;
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return type.isInstance(values) ? Optional.of(type.cast(values)) : Optional.empty();
    }

    private <T> Optional<T> optionalValue(String name, Class<T> type, String jsonType) {
        var value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw wrongType(name, jsonType, value);
        }
        return Optional.of(type.cast(value));
    }

    private static @Nullable Object copyValue(@Nullable Object value, String path) {
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
                for (var item : list) {
                    copy.add(copyValue(item, path));
                }
                return Collections.unmodifiableList(copy);
            }
            case JsonObject object -> {
                return copyValue(object.asMap(), path);
            }
            default -> {}
        }
        throw invalidValue(path, "unsupported value type " + value.getClass().getName());
    }

    private static BigDecimal decimal(Number value) {
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

    private static IllegalArgumentException wrongType(String name, String expected, Object actual) {
        return new IllegalArgumentException("JSON property '%s' must be %s, but was %s"
                .formatted(name, expected, actual.getClass().getSimpleName()));
    }

    private static IllegalArgumentException invalidNumber(String name, String expected, ArithmeticException cause) {
        return new IllegalArgumentException("JSON property '%s' is not an exact %s".formatted(name, expected), cause);
    }

    private static IllegalArgumentException invalidValue(String path, String reason) {
        return new IllegalArgumentException("Invalid JSON value at '%s': %s".formatted(path, reason));
    }

    private static String writeJson(Object value) {
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
