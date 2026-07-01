/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Marks types that carry optional metadata ({@code _meta}) for protocol extensions. */
public interface HasMeta {
    /** Optional metadata map for protocol extensions. */
    @Nullable
    Map<String, JsonNode> meta();
}
