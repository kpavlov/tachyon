/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty.sse;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.util.AttributeKey;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Keeps long-lived SSE streams alive across idle periods. A channel carrying an open SSE response is
 * marked via {@link #enable(Channel, Duration)} which starts a scheduler that periodically writes
 * an SSE comment heartbeat ({@code :\r\n}). The scheduler runs on the channel's event loop.
 *
 * <p>A heartbeat byte flowing through the pipeline triggers {@link dev.tachyonmcp.transport.netty.SessionTouchHandler}
 * which refreshes session liveness — no scattered {@code touch()} calls needed.
 *
 * <p>A heartbeat is itself a chunk write, so a failed write (dead client, RST) closes the channel.
 *
 * <p>Call with {@code interval <= 0} to disable heartbeats (silent SSE channels then close on idle
 * via the existing idle handler).
 */
public final class SseHeartbeat {

    private static final AttributeKey<Boolean> ACTIVE = AttributeKey.valueOf("sseHeartbeatActive");
    private static final AttributeKey<ScheduledFuture<?>> HEARTBEAT_FUTURE = AttributeKey.valueOf("sseHeartbeatFuture");

    private SseHeartbeat() {}

    /**
     * Enables periodic heartbeats on {@code channel} at the given {@code interval}.
     * {@code interval <= 0} ({@link Duration#isZero()} or negative) disables heartbeats entirely.
     * Call after the SSE response headers are written.
     */
    public static void enable(Channel channel, Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        channel.attr(ACTIVE).set(Boolean.TRUE);
        var millis = interval.toMillis();
        var future =
                channel.eventLoop().scheduleAtFixedRate(() -> send(channel), millis, millis, TimeUnit.MILLISECONDS);
        channel.attr(HEARTBEAT_FUTURE).set(future);
        channel.closeFuture().addListener(ignored -> {
            var f = channel.attr(HEARTBEAT_FUTURE).getAndSet(null);
            if (f != null) {
                f.cancel(false);
            }
        });
    }

    /** @return {@code true} if {@code channel} carries an open SSE stream. */
    public static boolean isEnabled(Channel channel) {
        return Boolean.TRUE.equals(channel.attr(ACTIVE).get());
    }

    /**
     * Writes an SSE comment heartbeat ({@code :\r\n}) on the channel, closing the channel
     * if the write fails. Skipped when the channel is inactive or already backpressured (bytes are
     * pending, so the stream is not actually idle).
     */
    public static void send(Channel channel) {
        if (!channel.isActive() || !channel.isWritable()) {
            return;
        }
        var buf = ByteBufUtil.writeAscii(channel.alloc(), ":\r\n");
        channel.writeAndFlush(new DefaultHttpContent(buf)).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) channel.close();
        });
    }
}
