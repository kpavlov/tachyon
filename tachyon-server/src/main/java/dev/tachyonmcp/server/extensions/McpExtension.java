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

/** Pluggable server extension that can add custom methods, capabilities, and lifecycle hooks. */
public interface McpExtension extends Extension<McpContext> {
    /** Returns the server settings to advertise in the initialize response. */
    default JsonNode serverSettings() {
        return JsonNodeFactory.instance.objectNode();
    }

    /** Returns the set of JSON-RPC methods this extension handles. */
    default Set<String> methods() {
        return Set.of();
    }

    /** Whether the extension expects a meta envelope for its handler params. */
    default boolean requiresMetaEnvelope() {
        return true;
    }

    /** Bootstraps the extension during server startup. */
    default void bootstrap(McpServer server) {}

    /** Called when a new client connection is initialised with the extension's client settings. */
    default void onConnectionInit(McpContext context, Map<String, JsonNode> clientSettings) {}
}
