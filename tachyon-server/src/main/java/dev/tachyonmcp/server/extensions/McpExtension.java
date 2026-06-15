/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.extensions;

import dev.tachyonmcp.runtime.Extension;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public interface McpExtension extends Extension<McpContext> {
    default JsonNode serverSettings() {
        return JsonNodeFactory.instance.objectNode();
    }

    default Set<String> methods() {
        return Set.of();
    }

    default boolean requiresMetaEnvelope() {
        return true;
    }

    default void bootstrap(McpServer server) {}

    default void onConnectionInit(McpContext context, Map<String, JsonNode> clientSettings) {}
}
