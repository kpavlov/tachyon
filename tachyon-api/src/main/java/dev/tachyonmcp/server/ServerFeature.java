/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

/** Common interface for server features (tools, resources, prompts, tasks). */
public interface ServerFeature<D extends ServerFeature.Descriptor> {

    D descriptor();

    interface Descriptor {
        /** Unique name of this feature. */
        String name();
    }
}
