/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.domain.HasMeta;

/**
 * Common interface for server features (tools, resources, prompts, tasks).
 *
 * @param <D> the descriptor type for this feature
 */
public interface ServerFeature<D extends ServerFeature.Descriptor> {

    D descriptor();

    interface Descriptor {
        /** Unique name of this feature. */
        String name();
    }

    interface Request extends HasMeta {}
}
