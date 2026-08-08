/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import dev.tachyonmcp.api.json.JsonArray;
import dev.tachyonmcp.api.json.JsonObject;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

record JacksonObjectJsonDocument(ObjectNode node) implements JsonObject, JacksonNodeBacked {

    @Override
    public boolean has(String name) {
        return node.has(name);
    }

    @Override
    public Optional<JsonObject> objectOpt(String name) {
        var value = fieldOrNull(name);
        if (value == null) return Optional.empty();
        if (!value.isObject()) throw wrongType(name, "object", value);
        return Optional.of(new JacksonObjectJsonDocument((ObjectNode) value));
    }

    @Override
    public Optional<JsonArray> arrayOpt(String name) {
        var value = fieldOrNull(name);
        if (value == null) return Optional.empty();
        if (!value.isArray()) throw wrongType(name, "array", value);
        return Optional.of(JsonArray.of(treeToList(value)));
    }

    @Override
    public Optional<String> stringOpt(String name) {
        var value = fieldOrNull(name);
        if (value == null) return Optional.empty();
        if (!value.isString()) throw wrongType(name, "string", value);
        return Optional.of(value.asString());
    }

    @Override
    public Optional<Boolean> boolOpt(String name) {
        var value = fieldOrNull(name);
        if (value == null) return Optional.empty();
        if (!value.isBoolean()) throw wrongType(name, "boolean", value);
        return Optional.of(value.asBoolean());
    }

    @Override
    public Optional<BigDecimal> decimalOpt(String name) {
        var value = fieldOrNull(name);
        if (value == null) return Optional.empty();
        if (!value.isNumber()) throw wrongType(name, "number", value);
        return Optional.of(value.decimalValue());
    }

    @Override
    public OptionalInt intOpt(String name) {
        var decimal = decimalOpt(name);
        if (decimal.isEmpty()) return OptionalInt.empty();
        try {
            return OptionalInt.of(decimal.orElseThrow().intValueExact());
        } catch (ArithmeticException e) {
            throw invalidNumber(name, "int", e);
        }
    }

    @Override
    public OptionalLong longOpt(String name) {
        var decimal = decimalOpt(name);
        if (decimal.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(decimal.orElseThrow().longValueExact());
        } catch (ArithmeticException e) {
            throw invalidNumber(name, "long", e);
        }
    }

    @Override
    public OptionalDouble doubleOpt(String name) {
        var decimal = decimalOpt(name);
        if (decimal.isEmpty()) return OptionalDouble.empty();
        var result = decimal.orElseThrow().doubleValue();
        if (!Double.isFinite(result)) throw invalidNumber(name, "finite double", null);
        return OptionalDouble.of(result);
    }

    @Override
    public Map<String, @Nullable Object> asMap() {
        var copy = new LinkedHashMap<String, @Nullable Object>();
        for (var entry : node.properties()) {
            copy.put(entry.getKey(), JsonUtils.mapper().treeToValue(entry.getValue(), Object.class));
        }
        return Collections.unmodifiableMap(copy);
    }

    private @Nullable JsonNode fieldOrNull(String name) {
        var value = node.get(name);
        return value == null || value.isNull() ? null : value;
    }

    @SuppressWarnings("unchecked")
    private static List<@Nullable Object> treeToList(JsonNode arrayNode) {
        return (List<@Nullable Object>) JsonUtils.mapper().treeToValue(arrayNode, List.class);
    }

    private static IllegalArgumentException wrongType(String name, String expected, JsonNode actual) {
        return new IllegalArgumentException(
                "JSON property '%s' must be %s, but was %s".formatted(name, expected, actual.getNodeType()));
    }

    private static IllegalArgumentException invalidNumber(
            String name, String expected, @Nullable ArithmeticException cause) {
        return new IllegalArgumentException("JSON property '%s' is not an exact %s".formatted(name, expected), cause);
    }
}
