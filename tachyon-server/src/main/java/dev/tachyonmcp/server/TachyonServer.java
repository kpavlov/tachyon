/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.domain.Args;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.prompts.PromptRegistry;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceRegistry;
import dev.tachyonmcp.server.features.tasks.TaskRegistry;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRegistry;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.List;
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

    /** Returns the tool registry. */
    ToolRegistry tools();

    /** Returns the resource registry. */
    ResourceRegistry resources();

    /** Returns the prompt registry. */
    PromptRegistry prompts();

    /** Returns the task registry. */
    TaskRegistry tasks();

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

    /** Returns the registered extensions. */
    List<ServerExtension> extensions();

    /**
     * @deprecated Use {@code tools().register(handler)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    default void registerTool(ToolHandler handler) {
        tools().register(handler);
    }

    /**
     * @deprecated Use {@link #tools()} and
     * {@link ToolRegistry#register(String, String, String, String, BiFunction)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    default void registerTool(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            BiFunction<InteractionContext, Args, ToolResult> fn) {
        tools().register(name, description, inputSchemaJson, outputSchemaJson, fn);
    }

    /**
     * @deprecated Use {@code resources().register(descriptor, handler)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    default void registerResource(ResourceDescriptor descriptor, ResourceHandler handler) {
        resources().register(descriptor, handler);
    }

    /**
     * @deprecated Use {@code prompts().register(descriptor, handler)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    default void registerPrompt(PromptDescriptor descriptor, PromptHandler handler) {
        prompts().register(descriptor, handler);
    }

    /**
     * @deprecated Use {@code tools().find(name)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    @Nullable
    default ToolDescriptor getTool(String name) {
        return tools().find(name).orElse(null);
    }

    @Override
    void close();

    static ServerBuilder builder() {
        return new ServerBuilder();
    }
}
