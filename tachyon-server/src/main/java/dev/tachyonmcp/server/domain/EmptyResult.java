/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** An empty response carrying only optional metadata. */
public record EmptyResult(@Nullable Map<String, JsonNode> meta) implements HasMeta {}
