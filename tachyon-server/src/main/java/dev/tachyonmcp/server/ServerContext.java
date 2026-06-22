/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.LoggingLevel;
import dev.tachyonmcp.server.session.McpSession;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public interface ServerContext {

    String sessionId();

    @Nullable
    String protocolVersion();

    void protocolVersion(String version);

    void setLoggingLevel(LoggingLevel level);

    @Nullable
    LoggingLevel getLoggingLevel();

    CompletableFuture<String> sendRequest(String method, Object params);

    McpSession session();

    McpServer mcpServer();
}
