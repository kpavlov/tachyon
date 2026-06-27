/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty.sse;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.util.AttributeKey;

/**
 * Keeps long-lived SSE streams alive across idle periods. A channel carrying an open SSE response is
 * marked via {@link #enable(Channel)}; the idle handler then emits an SSE comment heartbeat
 * ({@code :\r\n}) on each idle tick instead of closing the channel, so neither the client's read
 * timeout nor an intermediary proxy reaps the otherwise silent connection.
 *
 * <p>A heartbeat is itself a chunk write, so a failed write (dead client, RST) closes the channel —
 * idle-close is no longer the dead-client detector once SSE channels stop closing on idle.
 */
public final class SseHeartbeat {

    private static final AttributeKey<Boolean> ACTIVE = AttributeKey.valueOf("sseHeartbeatActive");

    private SseHeartbeat() {}

    /** Marks {@code channel} as carrying an open SSE stream. Call after the SSE response headers are written. */
    public static void enable(Channel channel) {
        channel.attr(ACTIVE).set(Boolean.TRUE);
    }

    /** @return {@code true} if {@code channel} carries an open SSE stream and should be kept alive across idle. */
    public static boolean isEnabled(Channel channel) {
        return Boolean.TRUE.equals(channel.attr(ACTIVE).get());
    }

    /**
     * Writes an SSE comment heartbeat ({@code :\r\n}) on the channel's event loop, closing the channel
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
