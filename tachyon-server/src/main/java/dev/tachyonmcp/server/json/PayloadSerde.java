/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.json;

import java.lang.reflect.Type;

/**
 * Pluggable payload serialization for structured values and arguments.
 *
 * <p>The primary contract is {@link String}-based because structuredContent is always parsed into
 * the protocol model by the Jackson parser before encoding — raw bytes never reach the wire — and
 * non-Jackson serdes (kotlinx.serialization, gson) emit Strings natively. {@code byte[]} round-trips
 * added conversions without saving any.
 *
 * <p>All parameters are non-null. Passing {@code null} to {@link #serialize(Object)} or
 * {@link #deserialize(String, java.lang.reflect.Type)} is undefined and may throw.
 *
 * <p>Values reaching {@link #serialize(Object)} must be types the implementation understands.
 * Jackson {@link tools.jackson.databind.JsonNode} and {@link RawJson} structured values bypass
 * the serde; maps carrying {@code JsonNode} values are serialized with Jackson.
 *
 * @author Konstantin Pavlov
 */
public interface PayloadSerde {

    /** Serializes a value to its JSON string representation. */
    String serialize(Object value);

    /** Deserializes a JSON string to the given target type. */
    <T> T deserialize(String json, Type targetType);

    /** Deserializes a JSON string to the given target class. */
    default <T> T deserialize(String json, Class<T> targetClass) {
        return deserialize(json, (Type) targetClass);
    }
}
