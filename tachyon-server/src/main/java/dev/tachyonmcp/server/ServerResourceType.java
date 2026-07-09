/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.annotations.InternalApi;

/** Common interface for named MCP resource types (tools, resources, prompts, tasks). */
@InternalApi
public interface ServerResourceType {
    /** Unique name of this resource type. */
    String name();
}
