/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.json.JsonUtils;
import dev.tachyonmcp.server.json.PayloadDeserializer;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public final class ToolArgs {

    private final Map<String, JsonNode> raw;
    private final @Nullable PayloadDeserializer deserializer;

    private ToolArgs(@Nullable Map<String, JsonNode> raw, @Nullable PayloadDeserializer deserializer) {
        this.raw = raw == null ? Map.of() : Map.copyOf(raw);
        this.deserializer = deserializer;
    }

    public static ToolArgs of(@Nullable Map<String, JsonNode> raw) {
        return new ToolArgs(raw, null);
    }

    public static ToolArgs of(@Nullable Map<String, JsonNode> raw, @Nullable PayloadDeserializer deserializer) {
        return new ToolArgs(raw, deserializer);
    }

    public boolean isEmpty() {
        return raw.isEmpty();
    }

    public boolean has(String key) {
        return raw.containsKey(key);
    }

    /** Returns an unmodifiable view of the raw argument map. */
    public Map<String, JsonNode> asMap() {
        return Collections.unmodifiableMap(raw);
    }

    public String stringValue(String key) {
        return node(key).asString();
    }

    public int intValue(String key) {
        return node(key).asInt();
    }

    public boolean boolValue(String key) {
        return node(key).asBoolean();
    }

    public double doubleValue(String key) {
        return node(key).asDouble();
    }

    public Optional<String> stringOpt(String key) {
        return has(key) ? Optional.of(raw.get(key).asString()) : Optional.empty();
    }

    public String stringOr(String key, String fallback) {
        return has(key) ? raw.get(key).asString() : fallback;
    }

    public int intOr(String key, int fallback) {
        return has(key) ? raw.get(key).asInt() : fallback;
    }

    public boolean boolOr(String key, boolean fallback) {
        return has(key) ? raw.get(key).asBoolean() : fallback;
    }

    public double doubleOr(String key, double fallback) {
        return has(key) ? raw.get(key).asDouble() : fallback;
    }

    /** Returns the full arguments as a JSON string. */
    public String rawJson() {
        try {
            return JsonUtils.writeString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize arguments to JSON", e);
        }
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

    /**
     * Returns the argument {@code key} as a {@link JsonNode}, throwing if missing.
     *
     * @throws InvalidArgumentException if the key is not present
     */
    public JsonNode node(String key) {
        var n = raw.get(key);
        if (n == null) throw new InvalidArgumentException(key, "required argument missing");
        return n;
    }

    /**
     * Returns the argument {@code key} as a {@link JsonNode}, or {@code null} if missing.
     * Unlike {@link #node(String)} this never throws — use when the key is optional.
     */
    @Nullable
    public JsonNode raw(String key) {
        return raw.get(key);
    }
}
