/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty.sse;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.transport.netty.http.HttpHelpers;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages SSE stream lifecycle for GET requests: opens stateful or stateless
 * streams via {@link NettySseConnection}, writes opening frames and priming
 * events, and replays missed events on reconnection.
 */
public class SseManager {

    private static final Logger logger = LoggerFactory.getLogger(SseManager.class);

    static final int SSE_RETRY_DELAY_MS = 3000;

    private final Server server;

    public SseManager(Server server) {
        this.server = server;
    }

    public void openStream(
            ChannelHandlerContext ctx, Session session, @Nullable String lastEventId, @Nullable String origin) {
        var holder = new NettySseConnection[1];
        var connection = new NettySseConnection(ctx.channel(), () -> {
            // Only reset the session if THIS connection is still the current one. A reconnect may
            // have already replaced it; wiping to NOOP here would orphan the newer channel.
            if (session.clearConnection(holder[0])) {
                session.touch();
                logger.debug("SSE connection closed for session={}", session.id());
            }
        });
        holder[0] = connection;
        session.connection(connection);

        writeOpeningFrames(ctx, origin, connection);

        if (lastEventId != null && !lastEventId.isEmpty()) {
            server.executor().execute(() -> replayEvents(session, lastEventId));
        }

        logger.debug("SSE stream opened for session={}", session.id());
    }

    public void openStatelessStream(ChannelHandlerContext ctx, @Nullable String origin) {
        var connection = new NettySseConnection(
                ctx.channel(),
                () -> logger.debug(
                        "Stateless SSE connection closed: {}", ctx.channel().remoteAddress()));

        writeOpeningFrames(ctx, origin, connection);

        logger.debug("Stateless SSE stream opened: {}", ctx.channel().remoteAddress());
    }

    private void writeOpeningFrames(ChannelHandlerContext ctx, @Nullable String origin, NettySseConnection connection) {
        var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHelpers.setSseStreamHeaders(response, origin);
        ctx.write(response);
        ctx.writeAndFlush(
                new DefaultHttpContent(ByteBufUtil.writeUtf8(ctx.alloc(), "retry: " + SSE_RETRY_DELAY_MS + "\n")));
        SseHeartbeat.enable(ctx.channel());
        var primeSse = new SseEvent(String.valueOf(server.nextEventId()), "message", "");
        connection.send(primeSse);
    }

    void replayEvents(Session session, String lastEventId) {
        try {
            var lastSseId = Long.parseLong(lastEventId);
            var replayed = server.replay(session.id(), -1);
            for (var event : replayed) {
                var sseId = event.sseEventId();
                if (sseId < 0 || sseId <= lastSseId) continue;
                var sseEvent = Server.toSseEvent(event);
                if (sseEvent == null) continue;
                if (!session.send(sseEvent)) break; // session closed or throttled mid-replay
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid Last-Event-ID: {}", lastEventId);
        }
    }
}
