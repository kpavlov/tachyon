/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.Notifications;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.ServerContext;
import org.jspecify.annotations.Nullable;

/** Dispatch context for a single handler invocation, providing access to the server, session, and notifications. */
public interface McpContext extends InteractionContext<McpSession> {

    /** Returns the server-level context. */
    ServerContext server();

    /** Returns the notification sender bound to this context. */
    Notifications notifications();

    @Override
    @Nullable
    McpSession session();

    /** Returns the protocol response mapper for the current protocol version. */
    default ProtocolResponseMapper responseMapper() {
        return server().mcpServer().responseMapper();
    }

    /** Returns the outbound SSE stream, or {@code null} if not yet upgraded. */
    @Nullable
    OutboundSseStream outboundStream();

    /** Sets the outbound SSE stream for this dispatch. */
    void setOutboundStream(@Nullable OutboundSseStream stream);

    /** Marks an extension as enabled for this session. */
    void enableExtension(String extensionId);

    default boolean isExtensionEnabled(String extensionId) {
        var s = session();
        return s != null && s.isExtensionEnabled(extensionId);
    }
}
