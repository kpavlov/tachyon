/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

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
            copy.put(name, JsonValues.copyValue(value, name));
        });
        this.values = Collections.unmodifiableMap(copy);
        this.json = JsonValues.writeJson(this.values);
    }

    @Override
    public String json() {
        return json;
    }

    @Override
    public boolean has(String name) {
        return values.containsKey(name);
    }

    @Override
    public Optional<JsonObject> objectOpt(String name) {
        return JsonValues.objectOpt(values.get(name), location(name));
    }

    @Override
    public Optional<JsonArray> arrayOpt(String name) {
        return JsonValues.arrayOpt(values.get(name), location(name));
    }

    @Override
    public Optional<String> stringOpt(String name) {
        return JsonValues.optionalValue(values.get(name), String.class, "string", location(name));
    }

    @Override
    public Optional<Boolean> boolOpt(String name) {
        return JsonValues.optionalValue(values.get(name), Boolean.class, "boolean", location(name));
    }

    @Override
    public Optional<BigDecimal> decimalOpt(String name) {
        return JsonValues.decimalOpt(values.get(name), location(name));
    }

    @Override
    public OptionalInt intOpt(String name) {
        return JsonValues.intOpt(values.get(name), location(name));
    }

    @Override
    public OptionalLong longOpt(String name) {
        return JsonValues.longOpt(values.get(name), location(name));
    }

    @Override
    public OptionalDouble doubleOpt(String name) {
        return JsonValues.doubleOpt(values.get(name), location(name));
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

    private static String location(String name) {
        return "property '" + name + "'";
    }
}
