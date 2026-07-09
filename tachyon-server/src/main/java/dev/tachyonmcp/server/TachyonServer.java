/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * The one public type users hold — a running MCP server that is {@link AutoCloseable}.
 *
 * <p>Created via {@link #builder()} (Java) or {@code TachyonServer(port) { }} (Kotlin).
 * Call {@link #close()} to shut down the transport and release all resources.
 *
 * <p>Kotlin users get {@code .use { }} from the stdlib via {@code AutoCloseable}.
 */
public interface TachyonServer extends AutoCloseable {

    /** Returns the port the server is bound to. */
    int port();

    /** Returns the host the server is bound to. */
    String host();

    /** Returns the server configuration. */
    ServerConfig config();

    /** Registers a tool handler. */
    void registerTool(ToolHandler handler);

    /** Registers a tool with string JSON schemas and a handler function. */
    void registerTool(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            BiFunction<dev.tachyonmcp.runtime.InteractionContext, ToolArgs, ToolResult> fn);

    /** Returns the descriptor for a tool by name, or {@code null} if not registered. */
    @Nullable
    ToolDescriptor getTool(String name);

    /** Returns the executor used for handler dispatch. */
    ExecutorService executor();

    /** Returns the registered extensions. */
    List<ServerExtension> extensions();

    @Override
    void close();

    static ServerBuilder builder() {
        return new ServerBuilder();
    }
}
