/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.annotations.InternalApi;

/** Common interface for server features (tools, resources, prompts, tasks). */
@InternalApi
public interface ServerFeature<D extends ServerFeature.Descriptor> {

    D descriptor();

    /**
     * @deprecated Use {@link #descriptor()}
     */
    @Deprecated
    default String name() {
        return descriptor().name();
    }

    interface Descriptor {
        /** Unique name of this feature. */
        String name();
    }
}
