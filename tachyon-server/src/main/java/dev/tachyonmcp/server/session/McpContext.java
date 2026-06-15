/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.Notifications;
import dev.tachyonmcp.server.ServerContext;
import org.jspecify.annotations.Nullable;

public interface McpContext extends InteractionContext<McpSession> {

    ServerContext server();

    Notifications notifications();

    @Override
    @Nullable
    McpSession session();

    default void enableExtension(String extensionId) {}

    default boolean isExtensionEnabled(String extensionId) {
        var s = session();
        return s != null && s.isExtensionEnabled(extensionId);
    }
}
