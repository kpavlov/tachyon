/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import dev.tachyonmcp.server.json.JsonObject;
import dev.tachyonmcp.server.json.PayloadDeserializer;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

public final class Args implements JsonObject {

    private final JsonObject values;
    private final @Nullable PayloadDeserializer deserializer;

    private static final Args EMPTY = new Args(JsonObject.empty(), null);

    private Args(JsonObject values, @Nullable PayloadDeserializer deserializer) {
        this.values = values;
        this.deserializer = deserializer;
    }

    public static Args of(@Nullable Map<String, ?> values) {
        return new Args(values == null ? JsonObject.empty() : JsonObject.of(values), null);
    }

    public static Args empty() {
        return EMPTY;
    }

    public static Args of(@Nullable Map<String, ?> values, @Nullable PayloadDeserializer deserializer) {
        return new Args(values == null ? JsonObject.empty() : JsonObject.of(values), deserializer);
    }

    public static Args from(JsonObject values, @Nullable PayloadDeserializer deserializer) {
        return new Args(values, deserializer);
    }

    public boolean isEmpty() {
        return values.asMap().isEmpty();
    }

    @Override
    public boolean contains(String name) {
        return values.contains(name);
    }

    @Override
    public Optional<JsonObject> objectOpt(String name) {
        return values.objectOpt(name);
    }

    @Override
    public Optional<String> stringOpt(String name) {
        return values.stringOpt(name);
    }

    @Override
    public Optional<Boolean> boolOpt(String name) {
        return values.boolOpt(name);
    }

    @Override
    public Optional<BigDecimal> decimalOpt(String name) {
        return values.decimalOpt(name);
    }

    @Override
    public OptionalInt intOpt(String name) {
        return values.intOpt(name);
    }

    @Override
    public OptionalLong longOpt(String name) {
        return values.longOpt(name);
    }

    @Override
    public OptionalDouble doubleOpt(String name) {
        return values.doubleOpt(name);
    }

    @Override
    public Map<String, Object> asMap() {
        return values.asMap();
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return values.unwrap(type);
    }

    @Override
    public String json() {
        return values.json();
    }

    /** Returns the full arguments as a JSON string. */
    public String rawJson() {
        return json();
    }

    /**
     * Decodes the full arguments into the given type using the configured serde.
     *
     * @throws IllegalStateException if no serde is configured
     * @throws InvalidArgumentException if the arguments cannot be decoded into {@code targetType};
     *     the dispatcher maps this to an invalid-params error rather than an internal failure
     */
    public <T> T decode(Type targetType) {
        if (deserializer == null) {
            throw new IllegalStateException("PayloadDeserializer is not configured for these args");
        }
        try {
            return deserializer.deserialize(rawJson(), targetType);
        } catch (InvalidArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            var msg = e.getMessage();
            if (msg != null) {
                var nl = msg.indexOf('\n');
                if (nl >= 0) msg = msg.substring(0, nl);
                var colon = msg.indexOf(": ");
                if (colon >= 0) msg = msg.substring(colon + 2);
            }
            throw new InvalidArgumentException("arguments", "could not be decoded: " + msg, e);
        }
    }

    /**
     * Decodes the full arguments into the given class using the configured serde.
     *
     * @throws IllegalStateException if no serde is configured
     */
    public <T> T decode(Class<T> targetClass) {
        return decode((Type) targetClass);
    }
}
