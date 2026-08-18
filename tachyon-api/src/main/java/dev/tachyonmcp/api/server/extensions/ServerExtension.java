/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.extensions;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.Extension;
import dev.tachyonmcp.api.runtime.InteractionContext;
import java.util.Set;

/** Pluggable server extension that can add custom methods, capabilities, and lifecycle hooks. */
@ExperimentalApi
public interface ServerExtension extends Extension<InteractionContext> {
    /** Returns the server settings to advertise in the initialize response. */
    default ExtensionSettings serverSettings() {
        return ExtensionSettings.empty();
    }

    /** Controls whether this extension is listed in the {@code initialize} response. See {@link AdvertiseMode}. */
    AdvertiseMode advertiseMode();

    /** Returns the set of JSON-RPC methods this extension handles. */
    default Set<String> methods() {
        return Set.of();
    }

    /** Whether the extension expects a meta envelope for its handler params. */
    default boolean requiresMetaEnvelope() {
        return true;
    }

    /** Bootstraps the extension during server startup. */
    default void bootstrap(ExtensionContext context) {}

    /** Called when a new client connection is initialised with the extension's client settings. */
    default void onConnectionInit(InteractionContext context, ExtensionSettings clientSettings) {}
}
