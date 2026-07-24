/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Marks types that carry optional metadata ({@code _meta}) for protocol extensions. */
public interface HasMeta {
    /** Optional metadata map for protocol extensions. */
    @Nullable
    Map<String, Object> meta();
}
