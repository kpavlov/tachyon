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
 * {@link dev.tachyonmcp.core.protocol.ProtocolRequestMapper.InitializeRequest#extensions()}),
 * 2026-07-28's {@code ExtensionNegotiationHandler} (declared per request, matches {@link
 * dev.tachyonmcp.core.protocol.ProtocolRequestMapper#declaredExtensions}), and {@link
 * DiscoverHandler}. Stateless — the registered extension list is a parameter, not instance state,
 * since there's nothing per-caller to own.
 */
public final class ExtensionNegotiator {

    private ExtensionNegotiator() {}

    /** Enables each registered extension the client declared, and fires its {@code onConnectionInit}. */
    public static void negotiate(
            List<ServerExtension> extensions, ChannelContext context, Map<String, JsonObject> declared) {
        for (var ext : extensions) {
            var clientSettings = declared.get(ext.extensionId());
            if (clientSettings != null) {
                context.enableExtension(ext.extensionId());
                ext.onConnectionInit(context, ExtensionSettings.of(clientSettings.asMap()));
            }
        }
    }

    /**
     * Returns every registered extension eligible for capability advertisement in this request's
     * response, along with its server settings: {@code ALWAYS}-mode extensions unconditionally,
     * {@code NEGOTIATED}-mode extensions only if this request already enabled them on {@code
     * context} (see {@link #negotiate}), and never {@code NEVER}-mode extensions.
     */
    public static Map<String, JsonObject> registeredExtensions(
            List<ServerExtension> extensions, ChannelContext context) {
        return extensions.stream()
                .filter(e -> switch (e.advertiseMode()) {
                    case ALWAYS -> true;
                    case NEVER -> false;
                    case NEGOTIATED -> context.isExtensionEnabled(e.extensionId());
                })
                .collect(Collectors.toMap(
                        Extension::extensionId, e -> e.serverSettings().values()));
    }
}
