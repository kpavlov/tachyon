/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.session.McpContext;

@FunctionalInterface
public interface ToolFn {

    Object apply(McpContext context, Object arguments) throws Exception;
}
