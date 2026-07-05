/*
 * Copyright (c) 2026 Konstantin Pavlov.
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
 * Jackson {@link tools.jackson.databind.JsonNode} and {@link RawJson} structured values bypass
 * the serde; maps carrying {@code JsonNode} values are serialized with Jackson.
 *
 * @author Konstantin Pavlov
 */
public interface PayloadSerde extends PayloadSerializer, PayloadDeserializer {}
