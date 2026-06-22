/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.builder;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Implementation;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ServerCapabilities;
import dev.tachyonmcp.server.JsonSchemaValidator;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.McpServerHandle;
import dev.tachyonmcp.server.ServerConfig;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.session.InMemorySessionLogRouter;
import dev.tachyonmcp.server.session.InMemorySessionStore;
import dev.tachyonmcp.server.session.SessionLogRouter;
import dev.tachyonmcp.server.session.SessionStore;
import dev.tachyonmcp.transport.netty.NettyServer;
import dev.tachyonmcp.transport.netty.NettyServerConfig;
import io.netty.channel.ChannelPipeline;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.JsonNodeFactory;

final class BuilderState {

    // McpServer identity
    @Nullable
    String name;

    @Nullable
    String version;

    @Nullable
    String description;

    @Nullable
    String websiteUrl;

    // Capability overrides (null = auto-detect from registries)
    @Nullable
    Boolean toolsListChanged;

    @Nullable
    Boolean resourcesSubscribe;

    @Nullable
    Boolean resourcesListChanged;

    @Nullable
    Boolean promptsListChanged;

    // Registry population
    final List<ToolHandler> tools = new ArrayList<>();

    final List<ResourceRegistration> resources = new ArrayList<>();
    final List<PromptRegistration> prompts = new ArrayList<>();
    final List<McpExtension> extensions = new ArrayList<>();

    record PromptRegistration(PromptDescriptor descriptor, PromptHandler handler) {}

    final List<TaskEntry> tasks = new ArrayList<>();

    record ResourceRegistration(
            ResourceDescriptor descriptor, @Nullable ResourceHandler handler) {}

    // Session
    @Nullable
    SessionLogRouter sessionLogRouter;

    Duration sessionTtl = Duration.ofSeconds(30);

    @Nullable
    SessionStore sessionStore;

    boolean stateless;

    // Validation
    JsonSchemaValidator jsonSchemaValidator = JsonSchemaValidator.noop();

    // Netty
    String host = "127.0.0.1";
    int port = -1;
    String endpointPath = "/mcp";
    Duration readerIdleTimeout = Duration.ofSeconds(60);
    Duration writerIdleTimeout = Duration.ofMinutes(5);

    @Nullable
    List<String> allowedOrigins;

    boolean allowNullOrigin = false;
    boolean allowPrivateNetworks = false;

    @Nullable
    List<String> allowedHeaders;

    boolean hostPortExplicitlySet;
    boolean addressExplicitlySet;

    @Nullable
    Consumer<ChannelPipeline> pipelineCustomizer;

    BuilderState() {}

    McpServer build() {
        var router = sessionLogRouter != null ? sessionLogRouter : new InMemorySessionLogRouter();
        var config = new ServerConfig(sessionTtl, stateless);
        var store = sessionStore != null ? sessionStore : new InMemorySessionStore();
        var allExtensions = Collections.unmodifiableList(extensions);
        var server = new McpServer(
                router, store, buildServerInfo(), buildCapabilities(), config, jsonSchemaValidator, allExtensions);
        tools.forEach(server::registerTool);
        resources.forEach(r -> {
            var d = r.descriptor();
            server.resources()
                    .add(
                            d,
                            r.handler() != null
                                    ? r.handler()
                                    : (_, _) -> new TextResourceContents(d.uri(), d.mimeType(), ""));
        });
        prompts.forEach(p -> server.prompts().add(p.descriptor(), p.handler()));
        tasks.forEach(t -> server.tasks().add(t));
        return server;
    }

    McpServerHandle bind() {
        if (port < 0) {
            throw new IllegalStateException("Port must be set before bind()");
        }
        var server = build();
        var config = new NettyServerConfig(
                host,
                port,
                endpointPath,
                readerIdleTimeout,
                writerIdleTimeout,
                NettyServerConfig.buildCorsConfig(
                        allowedOrigins, allowNullOrigin, allowPrivateNetworks, allowedHeaders),
                pipelineCustomizer);
        var netty = new NettyServer(server, config);
        return new McpServerHandle(server, netty.port(), netty);
    }

    private Implementation buildServerInfo() {
        return Implementation.builder()
                .name(name != null ? name : "tachyon-mcp")
                .version(version != null ? version : "0.1")
                .description(description)
                .websiteUrl(websiteUrl)
                .build();
    }

    private ServerCapabilities buildCapabilities() {
        final var builder = ServerCapabilities.builder();
        if (toolsListChanged != null) {
            builder.tools(new ServerCapabilities.Tools(toolsListChanged));
        }
        if (resourcesSubscribe != null || resourcesListChanged != null) {
            builder.resources(new ServerCapabilities.Resources(resourcesSubscribe, resourcesListChanged));
        }
        if (promptsListChanged != null) {
            builder.prompts(new ServerCapabilities.Prompts(promptsListChanged));
        }
        var logging = JsonNodeFactory.instance.objectNode();
        builder.logging(logging);
        var completions = JsonNodeFactory.instance.objectNode();
        builder.completions(completions);
        return builder.build();
    }
}
