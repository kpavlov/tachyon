/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.json;

import java.lang.reflect.Type;

/**
 * Pluggable payload deserialization for structured values and arguments.
 *
 * <p>The primary contract is {@link String}-based because structuredContent is always parsed into
 * the protocol model by the Jackson parser before encoding — raw bytes never reach the wire — and
 * non-Jackson serdes (kotlinx.serialization, gson) emit Strings natively. {@code byte[]} round-trips
 * added conversions without saving any.
 *
 * <p>All parameters are non-null. Passing {@code null} to
 * {@link #deserialize(String, Type)} is undefined and may throw.
 *
 * @author Konstantin Pavlov
 */
public interface PayloadDeserializer {

    /**
     * Deserializes a JSON string to the given target type.
     */
    <T> T deserialize(String json, Type targetType);

    /**
     * Deserializes a JSON string to the given target class.
     */
    default <T> T deserialize(String json, Class<T> targetClass) {
        return deserialize(json, (Type) targetClass);
    }
}
