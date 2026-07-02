/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.runtime.MutableInteractionContext;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.domain.LoggingLevel;
import org.jspecify.annotations.Nullable;

/**
 * Internal dispatch surface — the rich context handed to dispatch plumbing, {@code RpcMethodHandler}s,
 * and extension lifecycle hooks. It exposes the server, protocol response mapper, and outbound SSE stream
 * on top of {@link MutableInteractionContext}.
 *
 * <p><strong>Not part of the tool-author API.</strong> Tool/resource/prompt handlers receive the narrow
 * {@link dev.tachyonmcp.runtime.InteractionContext}; this type may change without notice.
 */
public interface DispatchContext extends MutableInteractionContext {

    /** Returns the owning {@link Server}. */
    Server server();

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
