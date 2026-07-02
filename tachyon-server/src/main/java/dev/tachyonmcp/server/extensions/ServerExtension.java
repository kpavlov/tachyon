/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.extensions;

import dev.tachyonmcp.runtime.Extension;
import dev.tachyonmcp.runtime.MutableInteractionContext;
import dev.tachyonmcp.server.Server;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/** Pluggable server extension that can add custom methods, capabilities, and lifecycle hooks. */
public interface ServerExtension extends Extension<MutableInteractionContext> {
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
    default void bootstrap(Server server) {}

    /** Called when a new client connection is initialised with the extension's client settings. */
    default void onConnectionInit(MutableInteractionContext context, Map<String, JsonNode> clientSettings) {}
}
