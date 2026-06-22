/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty.sse;

import dev.tachyonmcp.server.session.SseEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;

/**
 * Serializes an outbound {@link SseEvent} into its {@code text/event-stream} wire framing, writing
 * straight into a pooled buffer from {@code alloc}. Keeps the framing (a transport concern) out of
 * {@link SseEvent} so the {@code mcp.server} model stays free of Netty types, and avoids the
 * intermediate {@code String} a {@code format()} helper would allocate on the hot write path.
 */
public final class SseSerializer {

    private SseSerializer() {}

    /**
     * Encodes {@code event} as {@code id: …\nevent: …\ndata: …\n\n}, splitting multi-line data into
     * one {@code data:} line per {@code \n} (matching the SSE spec). The returned buffer is owned by
     * the caller and must be released after the write completes.
     */
    public static ByteBuf encode(ByteBufAllocator alloc, SseEvent event) {
        var buf = alloc.buffer();
        try {
            ByteBufUtil.writeAscii(buf, "id: ");
            ByteBufUtil.writeUtf8(buf, event.id());
            buf.writeByte('\n');
            ByteBufUtil.writeAscii(buf, "event: ");
            ByteBufUtil.writeUtf8(buf, event.event());
            buf.writeByte('\n');
            var data = event.data();
            var start = 0;
            while (true) {
                var nl = data.indexOf('\n', start);
                var end = nl < 0 ? data.length() : nl;
                ByteBufUtil.writeAscii(buf, "data: ");
                ByteBufUtil.writeUtf8(buf, data, start, end);
                buf.writeByte('\n');
                if (nl < 0) break;
                start = nl + 1;
            }
            buf.writeByte('\n');
            return buf;
        } catch (RuntimeException e) {
            buf.release();
            throw e;
        }
    }
}
