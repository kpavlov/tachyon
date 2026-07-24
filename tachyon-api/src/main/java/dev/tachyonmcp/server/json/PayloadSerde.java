/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

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
 * <p>Values reaching {@link #serialize(Object)} must be typed the implementation understands.
 * {@link JsonDocument} values bypass the serde.
 *
 * @author Konstantin Pavlov
 */
public interface PayloadSerde extends PayloadSerializer, PayloadDeserializer {}
