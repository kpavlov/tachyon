/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.server;

import dev.tachyonmcp.protocol.api.server.domain.HasMeta;

/**
 * Common interface for server features (tools, resources, prompts, tasks).
 *
 * @param <D> the descriptor type for this feature
 */
public interface ServerFeature<D extends ServerFeature.Descriptor> {

    /**
     * Returns the metadata descriptor for this feature.
     *
     * @return the descriptor
     */
    D descriptor();

    interface Descriptor {
        /** Unique name of this feature. */
        String name();
    }

    interface Request extends HasMeta {}
}
