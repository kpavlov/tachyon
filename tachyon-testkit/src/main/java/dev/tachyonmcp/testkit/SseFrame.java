/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One SSE event frame parsed off the wire by {@link SseSocketSubscriber}: its {@code id:} line,
 * if any, and its raw {@code data:} line.
 *
 * @param id   the SSE event id, or {@code null} if the frame carried none
 * @param data the frame's raw data payload
 */
public record SseFrame(@Nullable String id, String data) {

    /**
     * Parses {@link #data} as JSON — every {@code data:} line on a Tachyon MCP server's SSE
     * stream is a JSON-RPC envelope, the same assumption {@link McpClient} already makes for
     * POST-delivered notifications (see {@code McpClient#captureNotifications}). Parsed lazily,
     * on the caller's thread, so a malformed payload fails the assertion that reads it rather
     * than the background reader thread.
     */
    public JsonNode json() {
        return McpClient.MAPPER.readTree(data);
    }
}
