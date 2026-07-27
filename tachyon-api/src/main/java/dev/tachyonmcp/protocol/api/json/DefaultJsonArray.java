/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

final class DefaultJsonArray implements JsonArray {

    static final JsonArray EMPTY = new DefaultJsonArray(List.of());

    private final List<Object> values;
    private final String json;
    private final Map<Integer, JsonObject> objects = new ConcurrentHashMap<>();
    private final Map<Integer, JsonArray> arrays = new ConcurrentHashMap<>();

    DefaultJsonArray(List<?> values) {
        Objects.requireNonNull(values, "values");
        var copy = new ArrayList<>(values.size());
        for (var item : values) {
            copy.add(JsonValues.copyValue(item, "[" + copy.size() + "]"));
        }
        this.values = Collections.unmodifiableList(copy);
        this.json = JsonValues.writeJson(this.values);
    }

    @Override
    public String json() {
        return json;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public Optional<JsonObject> objectOpt(int index) {
        var value = element(index);
        return value == null
                ? Optional.empty()
                : Optional.of(objects.computeIfAbsent(index, ignored -> JsonValues.object(value, location(index))));
    }

    @Override
    public Optional<JsonArray> arrayOpt(int index) {
        var value = element(index);
        return value == null
                ? Optional.empty()
                : Optional.of(arrays.computeIfAbsent(index, ignored -> JsonValues.array(value, location(index))));
    }

    @Override
    public Optional<String> stringOpt(int index) {
        return JsonValues.optionalValue(element(index), String.class, "string", location(index));
    }

    @Override
    public Optional<Boolean> boolOpt(int index) {
        return JsonValues.optionalValue(element(index), Boolean.class, "boolean", location(index));
    }

    @Override
    public Optional<BigDecimal> decimalOpt(int index) {
        return JsonValues.decimalOpt(element(index), location(index));
    }

    @Override
    public OptionalInt intOpt(int index) {
        return JsonValues.intOpt(element(index), location(index));
    }

    @Override
    public OptionalLong longOpt(int index) {
        return JsonValues.longOpt(element(index), location(index));
    }

    @Override
    public OptionalDouble doubleOpt(int index) {
        return JsonValues.doubleOpt(element(index), location(index));
    }

    @Override
    public <T> List<T> valuesAs(Class<T> element) {
        Objects.requireNonNull(element, "element");
        var result = new ArrayList<T>(values.size());
        for (var index = 0; index < values.size(); index++) {
            result.add(coerce(values.get(index), element, index));
        }
        return List.copyOf(result);
    }

    @Override
    public List<Object> asList() {
        return values;
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return type.isInstance(values) ? Optional.of(type.cast(values)) : Optional.empty();
    }

    private @Nullable Object element(int index) {
        Objects.checkIndex(index, values.size());
        return values.get(index);
    }

    @SuppressWarnings("unchecked")
    private <T> T coerce(@Nullable Object value, Class<T> element, int index) {
        var location = location(index);
        if (value == null) {
            throw new IllegalArgumentException("JSON %s is null".formatted(location));
        }
        if (element == String.class) {
            return (T) JsonValues.optionalValue(value, String.class, "string", location)
                    .orElseThrow();
        }
        if (element == Boolean.class) {
            return (T) JsonValues.optionalValue(value, Boolean.class, "boolean", location)
                    .orElseThrow();
        }
        if (element == BigDecimal.class) {
            return (T) JsonValues.decimalOpt(value, location).orElseThrow();
        }
        if (element == Integer.class) {
            return (T) Integer.valueOf(JsonValues.intOpt(value, location).orElseThrow());
        }
        if (element == Long.class) {
            return (T) Long.valueOf(JsonValues.longOpt(value, location).orElseThrow());
        }
        if (element == Double.class) {
            return (T) Double.valueOf(JsonValues.doubleOpt(value, location).orElseThrow());
        }
        if (element == JsonObject.class) {
            return (T) objectOpt(index).orElseThrow();
        }
        if (element == JsonArray.class) {
            return (T) arrayOpt(index).orElseThrow();
        }
        throw new IllegalArgumentException("Unsupported JSON element type %s; decode the payload with a serde instead"
                .formatted(element.getName()));
    }

    private static String location(int index) {
        return "element [" + index + "]";
    }
}
