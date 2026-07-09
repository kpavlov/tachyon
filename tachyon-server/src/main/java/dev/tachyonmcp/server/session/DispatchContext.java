/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.runtime.ChannelContext;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.internal.ServerEngine;
import org.jspecify.annotations.Nullable;

@InternalApi
public interface DispatchContext extends ChannelContext {

    /** Returns the owning {@link ServerEngine}. */
    ServerEngine engine();

    /** Sets the logging level for the current session; no-op when no session is bound. */
    void setLoggingLevel(LoggingLevel level);

    /** Returns the logging level for the current session, or {@code null} when unset or session-less. */
    @Nullable
    LoggingLevel getLoggingLevel();

    /** Returns the protocol response mapper for the current protocol version. */
    ProtocolResponseMapper responseMapper();

    /** Returns the outbound SSE stream, or {@code null} if not yet upgraded. */
    @Nullable
    OutboundSseStream outboundStream();

    /** Sets the outbound SSE stream for this dispatch. */
    void setOutboundStream(@Nullable OutboundSseStream stream);
}
