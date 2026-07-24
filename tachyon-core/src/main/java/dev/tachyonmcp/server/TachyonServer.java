/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.completions.Completions;
import dev.tachyonmcp.server.features.prompts.Prompts;
import dev.tachyonmcp.server.features.resources.Resources;
import dev.tachyonmcp.server.features.tasks.Tasks;
import dev.tachyonmcp.server.features.tools.Tools;
import java.util.List;

/**
 * The one public type users hold — a running MCP server that is {@link AutoCloseable}.
 *
 * <p>Created via {@link #builder()} (Java) or {@code TachyonServer(port) { }} (Kotlin).
 * Call {@link #close()} to shut down the transport and release all resources.
 *
 * <p>Kotlin users get {@code .use { }} from the stdlib via {@code AutoCloseable}.
 */
public interface TachyonServer extends AutoCloseable {

    /**
     * Returns the tool registry.
     */
    Tools tools();

    /**
     * Returns the resource registry.
     */
    Resources resources();

    /**
     * Returns the prompt registry.
     */
    Prompts prompts();

    /**
     * Returns the task registry.
     */
    Tasks tasks();

    /**
     * Returns the completion registry.
     */
    Completions completions();

    Notifications notifications();

    /**
     * Returns the port the server is bound to, or 0 if not yet started.
     * Only meaningful after {@link ServerBuilder#start()} or equivalent.
     */
    int port();

    /**
     * Returns the host the server is bound to, or the configured host if not yet started.
     * Only meaningful after {@link ServerBuilder#start()} or equivalent.
     */
    String host();

    /**
     * Returns the server configuration.
     */
    ServerConfig config();

    /**
     * Returns the registered extensions.
     */
    List<ServerExtension> extensions();

    /**
     * Shuts down the server and releases its resources.
     */
    @Override
    void close();

    static ServerBuilder builder() {
        return new ServerBuilder();
    }
}
