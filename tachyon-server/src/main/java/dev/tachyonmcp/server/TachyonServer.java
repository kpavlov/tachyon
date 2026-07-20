/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.completions.Completions;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.prompts.Prompts;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.resources.Resources;
import dev.tachyonmcp.server.features.tasks.Tasks;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolFn;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.Tools;
import java.util.List;
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
    Tools tools();

    /** Returns the resource registry. */
    Resources resources();

    /** Returns the prompt registry. */
    Prompts prompts();

    /** Returns the task registry. */
    Tasks tasks();

    /** Returns the completion registry. */
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

    /** Returns the server configuration. */
    ServerConfig config();

    /** Returns the registered extensions. */
    List<ServerExtension> extensions();

    /**
     * Registers a tool handler with the server.
     *
     * @param handler the tool handler to register
     * @deprecated Use {@code tools().register(handler)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    default void registerTool(ToolHandler handler) {
        tools().register(handler);
    }

    /**
     * Registers a tool with the server.
     *
     * @param name              the tool name
     * @param description       the tool description
     * @param inputSchemaJson   the JSON schema for tool inputs
     * @param outputSchemaJson  the JSON schema for tool outputs
     * @param fn                the function that handles tool invocations
     * @deprecated Use {@link #tools()} and
     *             {@link Tools#register(String, String, String, String, ToolFn)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    default void registerTool(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            ToolFn fn) {
        tools().register(name, description, inputSchemaJson, outputSchemaJson, fn);
    }

    /**
     * Registers a resource descriptor and its handler.
     *
     * @param descriptor the resource descriptor
     * @param handler    the handler for resource requests
     * @deprecated Use {@code resources().register(descriptor, handler)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    default void registerResource(ResourceDescriptor descriptor, ResourceHandler handler) {
        resources().register(descriptor, handler);
    }

    /**
     * Registers a prompt handler with the server.
     *
     * @deprecated Use {@code prompts().register(descriptor, handler)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    default void registerPrompt(PromptDescriptor descriptor, PromptHandler handler) {
        prompts().register(descriptor, handler);
    }

    /**
     * Finds a registered tool descriptor by name.
     *
     * @param name the tool name
     * @return the matching tool descriptor, or {@code null} if no tool is registered with that name
     * @deprecated Use {@code tools().find(name)}.
     */
    @Deprecated(since = "1.0.0-beta.10")
    @Nullable
    default ToolDescriptor getTool(String name) {
        return tools().find(name).orElse(null);
    }

    /**
     * Shuts down the server and releases its resources.
     */
    @Override
    void close();

    static ServerBuilder builder() {
        return new ServerBuilder();
    }
}
