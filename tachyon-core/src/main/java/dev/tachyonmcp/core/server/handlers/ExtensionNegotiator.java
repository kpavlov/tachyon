/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.runtime.Extension;
import dev.tachyonmcp.api.server.extensions.ExtensionSettings;
import dev.tachyonmcp.api.server.extensions.ServerExtension;
import dev.tachyonmcp.core.runtime.ChannelContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Matches a client's declared extensions (ID to client settings) against the server's registered
 * {@link ServerExtension}s, enabling each match on the given {@link ChannelContext} and firing its
 * {@link ServerExtension#onConnectionInit}. Takes the minimal type it needs (just
 * {@code enableExtension}/{@code isExtensionEnabled}, both on {@code ChannelContext}) rather than the
 * wider {@code DispatchContext}, since 2026-07-28's negotiation runs in the Netty pipeline, before a
 * {@code DispatchContext} exists.
 *
 * <p>Shared by 2025-11-25's {@link InitializeHandler} (declared once via {@code initialize}, matches
 * {@link dev.tachyonmcp.core.protocol.ProtocolRequestMapper.InitializeRequest#extensions()}) and
 * 2026-07-28's {@code ExtensionNegotiationHandler} (declared per request, matches {@link
 * dev.tachyonmcp.core.protocol.ProtocolRequestMapper#declaredExtensions}) — both produce the same
 * {@code Map<String, JsonObject>} shape.
 */
public final class ExtensionNegotiator {

    private final List<ServerExtension> extensions;

    public ExtensionNegotiator(List<ServerExtension> extensions) {
        this.extensions = extensions;
    }

    /** Enables each registered extension the client declared, and fires its {@code onConnectionInit}. */
    public void negotiate(ChannelContext context, Map<String, JsonObject> declared) {
        for (var ext : extensions) {
            var clientSettings = declared.get(ext.extensionId());
            if (clientSettings != null) {
                context.enableExtension(ext.extensionId());
                ext.onConnectionInit(context, ExtensionSettings.of(clientSettings.asMap()));
            }
        }
    }

    /** Returns the currently-enabled subset of registered extensions, for echoing back in a response. */
    public Map<String, JsonObject> negotiatedExtensions(ChannelContext context) {
        return extensions.stream()
                .filter(e -> context.isExtensionEnabled(e.extensionId()))
                .collect(Collectors.toMap(
                        Extension::extensionId, e -> e.serverSettings().values()));
    }
}
