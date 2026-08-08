/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import dev.tachyonmcp.api.json.JsonArray;
import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Arguments container wrapping a {@link JsonObject} with optional deserialization support.
 */
public final class Args implements JsonObject {

    private final JsonObject values;
    private final @Nullable PayloadDeserializer deserializer;

    private static final Args EMPTY = new Args(JsonObject.empty(), null);

    private Args(JsonObject values, @Nullable PayloadDeserializer deserializer) {
        this.values = values;
        this.deserializer = deserializer;
    }

    /**
     * Creates arguments from a map of values.
     *
     * @param values the argument values, whose values may be {@code null}, or {@code null} for empty
     * @return a new Args instance
     */
    public static Args of(@Nullable Map<String, ? extends @Nullable Object> values) {
        return new Args(values == null ? JsonObject.empty() : JsonObject.of(values), null);
    }

    /**
     * Returns a shared empty arguments instance.
     *
     * @return empty args
     */
    public static Args empty() {
        return EMPTY;
    }

    /**
     * Creates arguments from a map of values with a deserializer.
     *
     * @param values      the argument values, whose values may be {@code null}, or {@code null} for empty
     * @param deserializer the payload deserializer, or {@code null}
     * @return a new Args instance
     */
    public static Args of(
            @Nullable Map<String, ? extends @Nullable Object> values, @Nullable PayloadDeserializer deserializer) {
        return new Args(values == null ? JsonObject.empty() : JsonObject.of(values), deserializer);
    }

    /**
     * Creates arguments from a JSON object with a deserializer.
     *
     * @param values      the JSON object containing argument values
     * @param deserializer the payload deserializer, or {@code null}
     * @return a new Args instance
     */
    public static Args from(JsonObject values, @Nullable PayloadDeserializer deserializer) {
        return new Args(values, deserializer);
    }

    /**
     * Returns whether the arguments are empty.
     *
     * @return {@code true} if no arguments are present
     */
    public boolean isEmpty() {
        return values.asMap().isEmpty();
    }

    @Override
    public boolean has(String name) {
        return values.has(name);
    }

    @Override
    public Optional<JsonObject> objectOpt(String name) {
        return values.objectOpt(name);
    }

    @Override
    public Optional<JsonArray> arrayOpt(String name) {
        return values.arrayOpt(name);
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
    public Map<String, @Nullable Object> asMap() {
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

    /**
     * Returns the JSON representation, so an accidental string concatenation of {@code Args}
     * (e.g. in a log line or a prompt/tool message) produces readable JSON instead of an opaque
     * object identity.
     *
     * @return the JSON string
     */
    @Override
    public String toString() {
        return json();
    }

    /**
     * Decodes the full arguments into the given type using the configured serde.
     *
     * @param <T>        the target type
     * @param targetType the target type
     * @return the decoded value
     * @throws IllegalStateException if no serde is configured
     * @throws InvalidArgumentException if the arguments cannot be decoded into {@code targetType};
     *     the dispatcher maps this to an invalid-params error rather than an internal failure
     */
    public <T> T decode(Type targetType) {
        if (deserializer == null) {
            throw new IllegalStateException("PayloadDeserializer is not configured for these args");
        }
        try {
            return deserializer.deserialize(json(), targetType);
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
     * @param <T>         the target type
     * @param targetClass the target class
     * @return the decoded value
     * @throws IllegalStateException if no serde is configured
     */
    public <T> T decode(Class<T> targetClass) {
        return decode((Type) targetClass);
    }
}
