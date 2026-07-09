/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

/** Common interface for named MCP resource types (tools, resources, prompts, tasks). */
public interface ServerResourceType {
    /** Unique name of this resource type. */
    String name();
}
