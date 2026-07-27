/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An empty response carrying only optional metadata.
 *
 * @param meta optional metadata
 */
public record EmptyResult(@Nullable Map<String, Object> meta) implements HasMeta {}
