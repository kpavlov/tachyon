/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** An empty response carrying only optional metadata. */
public record EmptyResult(@Nullable Map<String, Object> meta) implements HasMeta {}
