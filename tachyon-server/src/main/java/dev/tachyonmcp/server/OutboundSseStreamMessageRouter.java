/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.session.McpSession;
import dev.tachyonmcp.server.session.SseEvent;
import org.jspecify.annotations.Nullable;

/**
 * Default {@link MessageRouter} that diverts events through an {@link OutboundSseStream} bound in
 * the current dispatch context when the dispatch session matches the target session. Otherwise lets
 * the caller fall through to the normal GET-SSE path.
 */
public final class OutboundSseStreamMessageRouter implements MessageRouter {

    private static final ScopedValue<OutboundSseStream> OUTBOUND_SSE_STREAM = ScopedValue.newInstance();
    private static final ScopedValue<String> DISPATCH_SESSION_ID = ScopedValue.newInstance();

    public static <T, X extends Exception> T withDispatchContext(
            String sessionId, @Nullable OutboundSseStream outboundSseStream, ScopedValue.CallableOp<T, X> action)
            throws X {
        if (outboundSseStream == null) {
            return ScopedValue.where(DISPATCH_SESSION_ID, sessionId).call(action);
        }
        return ScopedValue.where(DISPATCH_SESSION_ID, sessionId)
                .where(OUTBOUND_SSE_STREAM, outboundSseStream)
                .call(action);
    }

    static void runWithDispatchContext(
            String sessionId, @Nullable OutboundSseStream outboundSseStream, Runnable action) {
        if (outboundSseStream == null) {
            ScopedValue.where(DISPATCH_SESSION_ID, sessionId).run(action);
            return;
        }
        ScopedValue.where(DISPATCH_SESSION_ID, sessionId)
                .where(OUTBOUND_SSE_STREAM, outboundSseStream)
                .run(action);
    }

    public static @Nullable OutboundSseStream currentOutboundSseStream() {
        return OUTBOUND_SSE_STREAM.isBound() ? OUTBOUND_SSE_STREAM.get() : null;
    }

    @Override
    public boolean tryRoute(McpSession session, SseEvent event) {
        var outboundStream = OUTBOUND_SSE_STREAM.isBound() ? OUTBOUND_SSE_STREAM.get() : null;
        if (outboundStream == null) return false;
        var currentSessionId = DISPATCH_SESSION_ID.isBound() ? DISPATCH_SESSION_ID.get() : null;
        if (currentSessionId == null || !currentSessionId.equals(session.id())) return false;
        outboundStream.start();
        outboundStream.writeEvent(event);
        return true;
    }
}
