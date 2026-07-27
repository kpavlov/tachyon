/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Marks types that carry optional metadata ({@code _meta}) for protocol extensions. */
public interface HasMeta {
    /**
     * Returns the optional metadata map for protocol extensions.
     *
     * @return the metadata map, or {@code null} if none
     */
    @Nullable
    Map<String, Object> meta();
}
