/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.extensions;

import dev.tachyonmcp.api.runtime.Extension;
import dev.tachyonmcp.core.runtime.ChannelContext;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/** Pluggable server extension that can add custom methods, capabilities, and lifecycle hooks. */
public interface ServerExtension extends Extension<ChannelContext> {
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
    default void bootstrap(ServerEngine server) {}

    /** Called when a new client connection is initialised with the extension's client settings. */
    default void onConnectionInit(ChannelContext context, Map<String, JsonNode> clientSettings) {}
}
