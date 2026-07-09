/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty.sse;

import static dev.tachyonmcp.transport.netty.sse.SseManager.SSE_RETRY_DELAY_MS;

import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.transport.netty.http.HttpHelpers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-POST SSE response stream. Lazily upgrades the POST response from JSON to an SSE stream
 * when the dispatching handler emits a server-to-client message.
 *
 * <p>All mutating operations are serialized onto the channel's {@link io.netty.channel.EventLoop},
 * matching Netty's "always work on the channel's EventLoop" idiom. The only field readable from
 * outside the EL is {@link #started} (queried by the dispatcher to decide JSON vs SSE response);
 * it is therefore {@code volatile}. The {@code closed} flag and the {@code queued} list are only
 * touched on the EL.
 */
public final class PostSseStream implements OutboundSseStream {

    private static final Logger logger = LoggerFactory.getLogger(PostSseStream.class);

    private final Channel channel;
    private final @Nullable String origin;
    private final LongSupplier eventIdSupplier;
    private final String streamKey;
    private final Duration heartbeatInterval;
    private final List<SseEvent> queued = new ArrayList<>();
    private volatile boolean started = false;
    private boolean closed = false;

    public PostSseStream(
            Channel channel, @Nullable String origin, LongSupplier eventIdSupplier, Duration heartbeatInterval) {
        this.channel = channel;
        this.origin = origin;
        this.eventIdSupplier = eventIdSupplier;
        this.heartbeatInterval = heartbeatInterval;
        // Session-unique key (one counter draw per POST) tagging this stream's events in the log
        // and suffixing its SSE ids, so Last-Event-ID resolves to THIS stream on replay. Not the
        // JSON-RPC request id — clients may reuse those across sequential requests.
        this.streamKey = String.valueOf(eventIdSupplier.getAsLong());
    }

    @Override
    public String streamKey() {
        return streamKey;
    }

    @Override
    public void start() {
        runOnEventLoop(this::doStart);
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void writeEvent(@Nullable SseEvent event) {
        if (event == null) return;
        runOnEventLoop(() -> doWriteEvent(event));
    }

    public void writeEvent(long sseEventId, ByteBuf body) {
        // The task owns `body`; if the event loop is shutting down and rejects it, the release
        // inside doWriteEvent never runs — release here instead of leaking.
        try {
            runOnEventLoop(() -> doWriteEvent(sseEventId, body));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            body.release();
        }
    }

    @Override
    public void comment(@Nullable String message) {
        // Self-starting: a comment upgrades the buffered POST to SSE even when no event was written
        // yet — this is the token-free keep-alive path. doStart is idempotent.
        runOnEventLoop(() -> {
            doStart();
            doWriteComment(message);
        });
    }

    @Override
    public void close() {
        runOnEventLoop(this::doClose);
    }

    private void runOnEventLoop(Runnable task) {
        var eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()) {
            task.run();
        } else {
            eventLoop.execute(task);
        }
    }

    /* ------- methods below run on the channel's EventLoop only ------- */

    private void doStart() {
        if (started || closed || !channel.isActive()) return;
        started = true;

        var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHelpers.setSseStreamHeaders(response, origin);
        channel.write(response);
        channel.write(
                new DefaultHttpContent(ByteBufUtil.writeUtf8(channel.alloc(), "retry: " + SSE_RETRY_DELAY_MS + "\n")));
        SseHeartbeat.enable(channel, heartbeatInterval);
        // Priming event: gives the client a Last-Event-ID baseline for reconnection (SEP-1699).
        // Carries this stream's key so a resume from the priming id replays only this stream.
        var primingId = eventIdSupplier.getAsLong();
        var priming = new SseEvent(Server.wireEventId(primingId, streamKey), "message", "");
        channel.write(new DefaultHttpContent(SseSerializer.encode(channel.alloc(), priming)));
        logger.trace("POST-SSE stream started, priming event id={}, channel={}", primingId, channel.id());
        for (var event : queued) {
            channel.write(new DefaultHttpContent(SseSerializer.encode(channel.alloc(), event)));
        }
        queued.clear();
        channel.flush();
    }

    private void doWriteEvent(SseEvent event) {
        if (closed || !channel.isActive()) {
            return;
        }
        if (!started) {
            queued.add(event);
            logger.trace("POST-SSE queued event (not started), id={}, data={}", event.id(), abbreviate(event.data()));
            return;
        }
        logger.trace("POST-SSE writing event, id={}, data={}", event.id(), abbreviate(event.data()));
        var buf = SseSerializer.encode(channel.alloc(), event);
        channel.writeAndFlush(new DefaultHttpContent(buf)).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                logger.warn(
                        "POST-SSE write failed, closing channel={}: {}",
                        channel.id(),
                        f.cause().getMessage());
                channel.close();
            }
        });
    }

    private void doWriteEvent(long sseEventId, ByteBuf body) {
        if (closed || !channel.isActive()) {
            body.release();
            return;
        }
        if (!started) {
            // Stream not yet upgraded — fall back to the String path; rare path, OK to decode here.
            try {
                queued.add(new SseEvent(
                        Server.wireEventId(sseEventId, streamKey), "message", body.toString(StandardCharsets.UTF_8)));
            } finally {
                body.release();
            }
            return;
        }
        var alloc = channel.alloc();
        var prefix = ByteBufUtil.writeAscii(
                alloc, "id: " + Server.wireEventId(sseEventId, streamKey) + "\nevent: message\ndata: ");
        var suffix = ByteBufUtil.writeAscii(alloc, "\n\n");
        var frame = alloc.compositeBuffer(3)
                .addComponent(true, prefix)
                .addComponent(true, body)
                .addComponent(true, suffix);
        channel.writeAndFlush(new DefaultHttpContent(frame)).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) channel.close();
        });
    }

    private void doWriteComment(@Nullable String message) {
        if (closed || !channel.isActive() || !started) return;
        // SSE comment = a line starting with ':'. Flatten embedded line breaks so the message
        // cannot inject extra SSE lines/events. Blank/null → bare ':' heartbeat.
        String line = message == null || message.isBlank()
                ? ":\r\n"
                : ": " + message.replace('\r', ' ').replace('\n', ' ') + "\r\n";
        var buf = ByteBufUtil.writeUtf8(channel.alloc(), line);
        channel.writeAndFlush(new DefaultHttpContent(buf)).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) channel.close();
        });
    }

    private void doClose() {
        if (closed) return;
        closed = true;
        if (!channel.isActive() || !started) return;
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
    }

    private static String abbreviate(String s) {
        return s != null && s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
