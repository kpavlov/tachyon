/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.prompts.InputRequiredPromptHandler;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
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

    /** Returns the server configuration. */
    ServerConfig config();

    /**
     * Registers a tool handler after the server has started (dynamic registration).
     *
     * <p><b>Intentional naming difference:</b> this is {@code registerTool()} (post-start
     * dynamic registration), while {@link ServerBuilder#tool(ToolHandler)} is
     * {@code tool()} (build-time DSL noun). They serve different lifecycle phases and are
     * intentionally named differently.
     */
    void registerTool(ToolHandler handler);

    /**
     * Registers a tool with string JSON schemas and a handler function
     * (post-start dynamic registration).
     *
     * <p><b>Intentional naming difference:</b> this is {@code registerTool()} (post-start),
     * while {@link ServerBuilder#tool(String, String, String, String, BiFunction)} is
     * {@code tool()} (build-time). They serve different lifecycle phases and are
     * intentionally named differently.
     */
    void registerTool(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            BiFunction<dev.tachyonmcp.runtime.InteractionContext, ToolArgs, ToolResult> fn);

    /**
     * Registers a resource after the server has started (dynamic registration).
     * When no handler is provided, returns empty content.
     */
    void registerResource(ResourceDescriptor descriptor);

    /**
     * Registers a resource with a read handler after the server has started.
     */
    void registerResource(ResourceDescriptor descriptor, ResourceHandler handler);

    /**
     * Registers a prompt with static messages after the server has started.
     */
    void registerPrompt(PromptDescriptor descriptor, List<PromptMessage> messages);

    /**
     * Registers a prompt with a dynamic handler (simple messages only) after the server has started.
     */
    void registerPrompt(PromptDescriptor descriptor, PromptHandler handler);

    /**
     * Registers a prompt with an input-required (MRTR) handler after the server has started.
     */
    void registerPrompt(PromptDescriptor descriptor, InputRequiredPromptHandler handler);

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
