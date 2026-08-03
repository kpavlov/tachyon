/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import java.util.HashMap;
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

    /**
     * Merges {@code additions} into {@code existing}, returning an immutable result. Entries in
     * {@code additions} win on key collisions.
     *
     * @param existing the current metadata map, or {@code null} if none
     * @param additions the metadata entries to merge in
     * @return an immutable merged metadata map
     */
    static Map<String, Object> merge(@Nullable Map<String, Object> existing, Map<String, Object> additions) {
        if (existing == null || existing.isEmpty()) return Map.copyOf(additions);
        var merged = new HashMap<>(existing);
        merged.putAll(additions);
        return Map.copyOf(merged);
    }
}
