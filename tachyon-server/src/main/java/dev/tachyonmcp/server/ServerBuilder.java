/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.config.CapabilitiesConfig;
import dev.tachyonmcp.server.config.MonitoringConfig;
import dev.tachyonmcp.server.config.NetworkConfig;
import dev.tachyonmcp.server.config.RuntimeConfig;
import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.config.ServerIdentity;
import dev.tachyonmcp.server.config.SessionConfig;
import dev.tachyonmcp.server.domain.Args;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.prompts.AsyncPromptHandler;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.prompts.PromptResult;
import dev.tachyonmcp.server.features.resources.AsyncResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JacksonPayloadSerde;
import dev.tachyonmcp.server.json.JsonConfig;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.json.NetworkntJsonSchemaValidator;
import dev.tachyonmcp.server.json.PayloadDeserializer;
import dev.tachyonmcp.server.json.PayloadSerializer;
import dev.tachyonmcp.server.session.InMemorySessionEventStore;
import dev.tachyonmcp.server.session.InMemorySessionStore;
import dev.tachyonmcp.transport.netty.NettyServer;
import dev.tachyonmcp.transport.netty.NettyServerConfig;
import io.netty.channel.ChannelPipeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for {@link TachyonServer}. Supports feature registration and configuration.
 */
public final class ServerBuilder {

    private final ServerIdentity.Builder identityBuilder = ServerIdentity.builder();
    private final CapabilitiesConfig.Builder capabilitiesConfig = CapabilitiesConfig.builder();
    private final FeaturesConfig featuresConfig = new FeaturesConfig();
    private final SessionConfig.Builder sessionBuilder = SessionConfig.builder();
    private final NetworkConfig.Builder networkBuilder = NetworkConfig.builder();
    private final RuntimeConfig.Builder runtimeBuilder = RuntimeConfig.builder();
    private final MonitoringConfig.Builder monitoringBuilder = MonitoringConfig.builder();

    @Nullable
    Consumer<ChannelPipeline> pipelineCustomizer;

    private @Nullable ExecutorService executor;
    private @Nullable ThreadFactory threadFactory;

    ServerBuilder() {}

    // === Configuration groups ===

    /**
     * Configures server identity (name, version, etc.).
     */
    public ServerBuilder info(Consumer<ServerIdentity.Builder> configurer) {
        configurer.accept(identityBuilder);
        return this;
    }

    /**
     * Configures which MCP capabilities are enabled.
     */
    public ServerBuilder capabilities(Consumer<CapabilitiesConfig.Builder> configurer) {
        configurer.accept(capabilitiesConfig);
        return this;
    }

    /**
     * Configures session lifecycle settings.
     */
    public ServerBuilder session(Consumer<SessionConfig.Builder> configurer) {
        configurer.accept(sessionBuilder);
        return this;
    }

    /**
     * Configures network settings (host, port, CORS, etc.).
     */
    public ServerBuilder network(Consumer<NetworkConfig.Builder> configurer) {
        configurer.accept(networkBuilder);
        return this;
    }

    /**
     * Configures handler-execution runtime settings (shutdown grace period, etc.).
     */
    public ServerBuilder runtime(Consumer<RuntimeConfig.Builder> configurer) {
        configurer.accept(runtimeBuilder);
        return this;
    }

    /**
     * Configures diagnostics and observability settings (slow-request logging, etc.).
     */
    public ServerBuilder monitoring(Consumer<MonitoringConfig.Builder> configurer) {
        configurer.accept(monitoringBuilder);
        return this;
    }

    /**
     * Configures JSON payload settings (serde, input/output schema validators).
     */
    public ServerBuilder json(Consumer<JsonConfig.Builder> configurer) {
        var builder = JsonConfig.builder();
        configurer.accept(builder);
        var config = builder.build();
        if (config.serializer() != null) {
            featuresConfig.payloadSerializer = config.serializer();
        }
        if (config.deserializer() != null) {
            featuresConfig.payloadDeserializer = config.deserializer();
        }
        if (config.inputValidator() != null) {
            featuresConfig.inputSchemaValidator = config.inputValidator();
        }
        if (config.outputValidator() != null) {
            featuresConfig.outputSchemaValidator = config.outputValidator();
        }
        return this;
    }

    /**
     * Sets the server name (shorthand for {@code info(b -> b.name(name))}).
     */
    public ServerBuilder name(String name) {
        identityBuilder.name(name);
        return this;
    }

    /**
     * Sets the listen port (shorthand for {@code network(b -> b.port(port))}).
     */
    public ServerBuilder port(int port) {
        networkBuilder.port(port);
        return this;
    }

    /**
     * Sets the server version (shorthand for {@code info(b -> b.version(version))}).
     */
    public ServerBuilder version(String version) {
        identityBuilder.version(version);
        return this;
    }

    /**
     * Sets the bind address (shorthand for {@code network(b -> b.host(host))}).
     */
    public ServerBuilder host(String host) {
        networkBuilder.host(host);
        return this;
    }

    // === Feature registration ===

    /**
     * Registers a tool handler to be added when the server is built.
     *
     * @param handler the tool handler to register
     * @return this builder
     */
    public ServerBuilder tool(ToolHandler handler) {
        featuresConfig.tools.add(handler);
        return this;
    }

    /**
     * Registers a synchronous tool handler with its descriptor.
     *
     * @param descriptor the tool descriptor
     * @param handler the function that handles tool interactions
     * @return this server builder
     */
    public ServerBuilder tool(ToolDescriptor descriptor, BiFunction<InteractionContext, Args, ToolResult> handler) {
        return tool(ToolHandler.of(descriptor, handler));
    }

    /**
     * Registers a synchronous tool using a descriptor builder.
     *
     * @param descriptor configures the tool descriptor
     * @param handler    handles tool invocations
     * @return this server builder
     */
    public ServerBuilder tool(
            Consumer<ToolDescriptor.Builder> descriptor, BiFunction<InteractionContext, Args, ToolResult> handler) {
        var builder = ToolDescriptor.builder();
        descriptor.accept(builder);
        return tool(builder.build(), handler);
    }

    /**
     * Registers an asynchronous tool handler with the specified descriptor.
     *
     * @param descriptor the tool descriptor
     * @param handler the function that handles tool interactions asynchronously
     * @return this server builder
     */
    public ServerBuilder asyncTool(
            ToolDescriptor descriptor, BiFunction<InteractionContext, Args, CompletionStage<ToolResult>> handler) {
        return tool(ToolHandler.ofAsync(descriptor, handler));
    }

    /**
     * Registers an asynchronous tool using a descriptor builder configuration.
     *
     * @param descriptor configures the tool descriptor
     * @param handler    handles tool invocations asynchronously
     * @return this server builder
     */
    public ServerBuilder asyncTool(
            Consumer<ToolDescriptor.Builder> descriptor,
            BiFunction<InteractionContext, Args, CompletionStage<ToolResult>> handler) {
        var builder = ToolDescriptor.builder();
        descriptor.accept(builder);
        return asyncTool(builder.build(), handler);
    }

    /**
     * Registers a tool with string JSON schemas and a handler function (build-time DSL).
     *
     * <p>Use {@link TachyonServer#tools()} for post-build dynamic registration.
     */
    public ServerBuilder tool(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            java.util.function.BiFunction<InteractionContext, Args, ToolResult> fn) {
        return tool(ToolHandler.of(
                b -> b.name(name)
                        .description(description)
                        .inputSchema(inputSchemaJson)
                        .outputSchema(outputSchemaJson),
                fn));
    }

    /**
     * Registers a resource with a read handler.
     */
    public ServerBuilder resource(ResourceDescriptor descriptor, ResourceHandler handler) {
        featuresConfig.resources.add(new FeaturesConfig.ResourceRegistration(descriptor, handler));
        return this;
    }

    /**
     * Registers a resource using a descriptor configured by the supplied builder.
     *
     * @param descriptor configures the resource descriptor
     * @param handler handles requests for the registered resource
     * @return this builder
     */
    public ServerBuilder resource(Consumer<ResourceDescriptor.Builder> descriptor, ResourceHandler handler) {
        var builder = ResourceDescriptor.builder();
        descriptor.accept(builder);
        return resource(builder.build(), handler);
    }

    /**
     * Registers an asynchronous resource handler.
     *
     * @param descriptor the resource descriptor
     * @param handler    the handler for resource requests
     * @return this builder
     */
    public ServerBuilder asyncResource(ResourceDescriptor descriptor, AsyncResourceHandler handler) {
        return resource(descriptor, handler);
    }

    /**
     * Registers an asynchronous resource using a descriptor configured by the supplied builder.
     *
     * @param descriptor configures the resource descriptor
     * @param handler handles asynchronous resource requests
     * @return this server builder
     */
    public ServerBuilder asyncResource(Consumer<ResourceDescriptor.Builder> descriptor, AsyncResourceHandler handler) {
        var builder = ResourceDescriptor.builder();
        descriptor.accept(builder);
        return asyncResource(builder.build(), handler);
    }

    /**
     * Registers a prompt with static messages (no handler, messages provided directly).
     */
    public ServerBuilder prompt(PromptDescriptor descriptor, List<PromptMessage> messages) {
        return prompt(descriptor, (ctx, request) -> PromptResult.messages(messages));
    }

    /**
     * Registers a prompt with a descriptor configured by the supplied builder consumer.
     *
     * @param descriptor consumer that configures the prompt descriptor
     * @param messages   messages returned when the prompt is invoked
     * @return this server builder
     */
    public ServerBuilder prompt(Consumer<PromptDescriptor.Builder> descriptor, List<PromptMessage> messages) {
        var builder = PromptDescriptor.builder();
        descriptor.accept(builder);
        return prompt(builder.build(), messages);
    }

    /**
     * Registers a prompt and its handler with the server.
     *
     * @param descriptor the prompt descriptor
     * @param handler    the handler invoked for prompt requests
     * @return this builder
     */
    public ServerBuilder prompt(PromptDescriptor descriptor, PromptHandler handler) {
        featuresConfig.prompts.add(new FeaturesConfig.PromptRegistration(descriptor, handler));
        return this;
    }

    /**
     * Registers a prompt using a descriptor builder and handler.
     *
     * @param descriptor configures the prompt descriptor
     * @param handler    handles prompt requests
     * @return this server builder
     */
    public ServerBuilder prompt(Consumer<PromptDescriptor.Builder> descriptor, PromptHandler handler) {
        var builder = PromptDescriptor.builder();
        descriptor.accept(builder);
        return prompt(builder.build(), handler);
    }

    /**
     * Registers an asynchronous prompt handler for a prompt descriptor.
     *
     * @param descriptor the prompt descriptor
     * @param handler the asynchronous prompt handler
     * @return this server builder
     */
    public ServerBuilder asyncPrompt(PromptDescriptor descriptor, AsyncPromptHandler handler) {
        return prompt(descriptor, handler);
    }

    /**
     * Registers an asynchronous prompt using a descriptor configured by the supplied builder.
     *
     * @param descriptor configures the prompt descriptor
     * @param handler handles prompt requests asynchronously
     * @return this builder
     */
    public ServerBuilder asyncPrompt(Consumer<PromptDescriptor.Builder> descriptor, AsyncPromptHandler handler) {
        var builder = PromptDescriptor.builder();
        descriptor.accept(builder);
        return prompt(builder.build(), handler);
    }

    /**
     * Registers a resource template.
     */
    public ServerBuilder resourceTemplate(ResourceTemplateDescriptor descriptor, ResourceHandler handler) {
        featuresConfig.templates.add(ResourceTemplateEntry.of(descriptor, handler));
        return this;
    }

    /**
     * Registers a resource template configured by the supplied builder.
     *
     * @param configBuilder configures the resource template descriptor
     * @param handler       handles requests for the registered resource template
     * @return this builder
     */
    public ServerBuilder resourceTemplate(
            Consumer<ResourceTemplateDescriptor.Builder> configBuilder, ResourceHandler handler) {
        var builder = ResourceTemplateDescriptor.builder();
        configBuilder.accept(builder);
        return resourceTemplate(builder.build(), handler);
    }

    /**
     * Registers an asynchronous resource template handler.
     *
     * @param descriptor the resource template descriptor
     * @param handler the handler invoked to resolve the resource template
     * @return this builder
     */
    public ServerBuilder asyncResourceTemplate(ResourceTemplateDescriptor descriptor, AsyncResourceHandler handler) {
        return resourceTemplate(descriptor, handler);
    }

    /**
     * Registers an asynchronous resource template using a descriptor configured by the supplied builder.
     *
     * @param descriptor configures the resource template descriptor
     * @param handler handles requests for the resource template
     * @return this builder
     */
    public ServerBuilder asyncResourceTemplate(
            Consumer<ResourceTemplateDescriptor.Builder> descriptor, AsyncResourceHandler handler) {
        var builder = ResourceTemplateDescriptor.builder();
        descriptor.accept(builder);
        return asyncResourceTemplate(builder.build(), handler);
    }

    /**
     * Registers a server extension.
     */
    public ServerBuilder extension(ServerExtension extension) {
        featuresConfig.extensions.add(extension);
        return this;
    }

    /**
     * Sets a caller-owned executor for handler dispatch. The server will not shut it down on close.
     * Must be thread-per-task (each task starts on a new thread); bounded pools deadlock with
     * the blocking-first dispatch contract. Mutually exclusive with {@link #threadFactory}.
     */
    public ServerBuilder executor(ExecutorService executor) {
        if (threadFactory != null) {
            throw new IllegalStateException("executor() and threadFactory() are mutually exclusive");
        }
        this.executor = executor;
        return this;
    }

    /**
     * Sets a thread factory for virtual-thread-per-task executor creation. The server owns this
     * executor and will shut it down on close. Mutually exclusive with {@link #executor}.
     */
    public ServerBuilder threadFactory(ThreadFactory threadFactory) {
        if (executor != null) {
            throw new IllegalStateException("executor() and threadFactory() are mutually exclusive");
        }
        this.threadFactory = threadFactory;
        return this;
    }

    static void validateExecutor(ExecutorService executor) {
        var thread1 = new Thread[1];
        var thread2 = new Thread[1];
        var latch = new CountDownLatch(1);
        try {
            var f1 = executor.submit(() -> {
                thread1[0] = Thread.currentThread();
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            var f2 = executor.submit(() -> thread2[0] = Thread.currentThread());
            f2.get(2, TimeUnit.SECONDS);
            latch.countDown();
            f1.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "executor must create a new (virtual) thread per task; bounded pools deadlock with blocking-first dispatch",
                    e);
        } finally {
            latch.countDown();
        }
        if (thread1[0] == thread2[0]) {
            throw new IllegalArgumentException(
                    "executor must create a new (virtual) thread per task; bounded pools deadlock with blocking-first dispatch");
        }
    }

    // === Transport escape hatch ===

    /**
     * Provides a customizer for the Netty channel pipeline.
     */
    public ServerBuilder pipelineCustomizer(@Nullable Consumer<ChannelPipeline> customizer) {
        this.pipelineCustomizer = customizer;
        return this;
    }

    // === Terminal methods ===

    /**
     * Builds a configured {@link TachyonServer} without binding a transport.
     *
     * <p>The returned server includes the configured sessions, features, extensions, payload
     * processing, and execution strategy. Transport-dependent host and port values become meaningful
     * only after {@link #start()}.
     *
     * @return the configured server
     */
    public TachyonServer build() {
        var sessionConfig = sessionBuilder.build();
        var sessionEventStore = sessionConfig.sessionEventStore() != null
                ? sessionConfig.sessionEventStore()
                : new InMemorySessionEventStore();
        var store = sessionConfig.sessionStore() != null ? sessionConfig.sessionStore() : new InMemorySessionStore();
        var allExtensions = Collections.unmodifiableList(featuresConfig.extensions);
        var serverConfig = buildConfig();
        ExecutorService resolvedExecutor;
        boolean ownsExecutor;
        if (executor != null) {
            validateExecutor(executor);
            resolvedExecutor = executor;
            ownsExecutor = false;
        } else if (threadFactory != null) {
            resolvedExecutor = Executors.newThreadPerTaskExecutor(threadFactory);
            ownsExecutor = true;
        } else {
            resolvedExecutor = DefaultTachyonServer.defaultExecutorForBuilder();
            ownsExecutor = true;
        }
        var server = new DefaultTachyonServer(
                resolvedExecutor,
                ownsExecutor,
                sessionEventStore,
                store,
                serverConfig,
                featuresConfig.inputSchemaValidator,
                featuresConfig.outputSchemaValidator,
                featuresConfig.payloadSerializer,
                featuresConfig.payloadDeserializer,
                allExtensions);
        featuresConfig.tools.forEach(server.tools()::register);
        featuresConfig.resources.forEach(r -> server.resources().register(r.descriptor(), r.handler()));
        featuresConfig.prompts.forEach(p -> server.prompts().register(p.descriptor(), p.handler()));
        featuresConfig.templates.forEach(t -> server.resources().registerTemplate(t.descriptor(), t.handler()));
        return server;
    }

    /**
     * Builds the server and starts the Netty transport (blocking). Requires a port to be set.
     */
    public TachyonServer start() {
        var networkConfig = networkBuilder.build();
        if (networkConfig.port() < 0) {
            throw new IllegalStateException("Port must be set before start()");
        }
        var server = (DefaultTachyonServer) build();
        var nettyConfig = new NettyServerConfig(
                networkConfig.host(),
                networkConfig.port(),
                networkConfig.endpointPath(),
                networkConfig.readerIdleTimeout(),
                networkConfig.writerIdleTimeout(),
                networkConfig.maxContentLength(),
                NettyServerConfig.buildCorsConfig(
                        networkConfig.allowedOrigins(),
                        networkConfig.allowNullOrigin(),
                        networkConfig.allowPrivateNetworks(),
                        networkConfig.allowedHeaders()),
                networkConfig.ioEngine(),
                pipelineCustomizer);
        var netty = new NettyServer(server, nettyConfig);
        server.bind(netty, netty.host(), netty.port());
        return server;
    }

    /**
     * Builds the {@link ServerConfig} from the current builder state.
     */
    public ServerConfig buildConfig() {
        return new ServerConfig(
                identityBuilder.build(),
                capabilitiesConfig.build(),
                sessionBuilder.build(),
                networkBuilder.build(),
                runtimeBuilder.build(),
                monitoringBuilder.build());
    }

    static final class FeaturesConfig {

        final List<ToolHandler> tools = new ArrayList<>();
        final List<ResourceRegistration> resources = new ArrayList<>();
        final List<ResourceTemplateEntry> templates = new ArrayList<>();
        final List<PromptRegistration> prompts = new ArrayList<>();
        final List<ServerExtension> extensions = new ArrayList<>();

        JsonSchemaValidator inputSchemaValidator = new NetworkntJsonSchemaValidator();
        JsonSchemaValidator outputSchemaValidator = new NetworkntJsonSchemaValidator();
        PayloadSerializer payloadSerializer = new JacksonPayloadSerde();
        PayloadDeserializer payloadDeserializer = new JacksonPayloadSerde();

        record PromptRegistration(PromptDescriptor descriptor, PromptHandler handler) {}

        record ResourceRegistration(ResourceDescriptor descriptor, ResourceHandler handler) {}
    }
}
