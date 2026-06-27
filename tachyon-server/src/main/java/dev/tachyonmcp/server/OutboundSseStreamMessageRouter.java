/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.session.McpSession;
import dev.tachyonmcp.server.session.SseEvent;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link MessageRouter} that diverts events through an {@link OutboundSseStream} bound in
 * the current dispatch context when the dispatch session matches the target session. Otherwise, lets
 * the caller fall through to the normal GET-SSE path.
 */
public final class OutboundSseStreamMessageRouter implements MessageRouter {

    private static final Logger logger = LoggerFactory.getLogger(OutboundSseStreamMessageRouter.class);
    private static final ThreadLocal<@Nullable OutboundSseStream> OUTBOUND_SSE_STREAM = new ThreadLocal<>();
    private static final ThreadLocal<@Nullable String> DISPATCH_SESSION_ID = new ThreadLocal<>();

    public static <T> T withDispatchContext(
            @Nullable String sessionId, @Nullable OutboundSseStream outboundSseStream, Callable<T> action)
            throws Exception {
        var prevSessionId = DISPATCH_SESSION_ID.get();
        var prevStream = OUTBOUND_SSE_STREAM.get();
        DISPATCH_SESSION_ID.set(sessionId);
        OUTBOUND_SSE_STREAM.set(outboundSseStream);
        try {
            return action.call();
        } finally {
            if (prevSessionId == null) DISPATCH_SESSION_ID.remove();
            else DISPATCH_SESSION_ID.set(prevSessionId);
            if (prevStream == null) OUTBOUND_SSE_STREAM.remove();
            else OUTBOUND_SSE_STREAM.set(prevStream);
        }
    }

    public static @Nullable OutboundSseStream currentOutboundSseStream() {
        return OUTBOUND_SSE_STREAM.get();
    }

    public static @Nullable String currentSessionId() {
        return DISPATCH_SESSION_ID.get();
    }

    @Override
    public boolean tryRoute(McpSession session, SseEvent event) {
        var outboundStream = OUTBOUND_SSE_STREAM.get();
        logger.trace("tryRoute: session={}, outboundStream={}", session.id(), outboundStream);
        if (outboundStream == null) return false;
        var currentSessionId = DISPATCH_SESSION_ID.get();
        if (currentSessionId == null || !currentSessionId.equals(session.id())) {
            logger.trace("tryRoute sessionId mismatch: current={}, expected={}", currentSessionId, session.id());
            return false;
        }
        outboundStream.start();
        outboundStream.writeEvent(event);
        logger.trace("tryRoute ROUTED");
        return true;
    }
}
