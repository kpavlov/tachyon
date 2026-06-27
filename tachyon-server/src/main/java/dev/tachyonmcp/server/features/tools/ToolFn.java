/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.session.McpContext;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@FunctionalInterface
public interface ToolFn<R extends ToolResult> {

    R apply(McpContext context, @Nullable Map<String, JsonNode> arguments) throws Exception;
}
