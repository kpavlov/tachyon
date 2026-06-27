/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty.sse;

import static dev.tachyonmcp.transport.netty.sse.SseManager.SSE_RETRY_DELAY_MS;

import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.session.SseEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
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
    private final List<SseEvent> queued = new ArrayList<>();
    private volatile boolean started = false;
    private boolean closed = false;

    public PostSseStream(Channel channel, @Nullable String origin, LongSupplier eventIdSupplier) {
        this.channel = channel;
        this.origin = origin;
        this.eventIdSupplier = eventIdSupplier;
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
        runOnEventLoop(() -> doWriteEvent(sseEventId, body));
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
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream")
                .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
                .set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
                .set(HttpHeaderNames.CONNECTION, "keep-alive");
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
        channel.write(response);
        channel.write(
                new DefaultHttpContent(ByteBufUtil.writeUtf8(channel.alloc(), "retry: " + SSE_RETRY_DELAY_MS + "\n")));
        // Priming event: gives the client a Last-Event-ID baseline for reconnection (SEP-1699).
        var primingId = eventIdSupplier.getAsLong();
        var priming = new SseEvent(String.valueOf(primingId), "message", "");
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
                        String.valueOf(sseEventId), "message", body.toString(java.nio.charset.StandardCharsets.UTF_8)));
            } finally {
                body.release();
            }
            return;
        }
        var alloc = channel.alloc();
        var prefix = ByteBufUtil.writeAscii(alloc, "id: " + sseEventId + "\nevent: message\ndata: ");
        var suffix = ByteBufUtil.writeAscii(alloc, "\n\n");
        var frame = alloc.compositeBuffer(3)
                .addComponent(true, prefix)
                .addComponent(true, body)
                .addComponent(true, suffix);
        channel.writeAndFlush(new DefaultHttpContent(frame)).addListener((ChannelFutureListener) f -> {
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
