/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.config.*;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.tools.AsyncToolHandler;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JacksonPayloadSerde;
import dev.tachyonmcp.server.json.JsonConfig;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.json.NetworkntJsonSchemaValidator;
import dev.tachyonmcp.server.json.PayloadDeserializer;
import dev.tachyonmcp.server.json.PayloadSerde;
import dev.tachyonmcp.server.json.PayloadSerializer;
import dev.tachyonmcp.server.session.InMemorySessionLogRouter;
import dev.tachyonmcp.server.session.InMemorySessionStore;
import dev.tachyonmcp.transport.netty.NettyServer;
import dev.tachyonmcp.transport.netty.NettyServerConfig;
import io.netty.channel.ChannelPipeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Fluent builder for {@link Server}. Supports feature registration and configuration. */
public final class ServerBuilder {

    private final ServerIdentityBuilder identityBuilder =
            ServerIdentity.builder().from(ServerIdentity.DEFAULT);
    private final CapabilitiesConfig.Builder capabilitiesConfig = CapabilitiesConfig.builder();
    private final FeaturesConfig featuresConfig = new FeaturesConfig();
    private final SessionConfig.Builder sessionBuilder = SessionConfig.builder();
    private final NetworkConfig.Builder networkBuilder = NetworkConfig.builder();
    private final RuntimeConfig.Builder runtimeBuilder = RuntimeConfig.builder();

    @Nullable
    Consumer<ChannelPipeline> pipelineCustomizer;

    private @Nullable ExecutorService executor;
    private @Nullable ThreadFactory threadFactory;

    ServerBuilder() {}

    // === Configuration groups ===

    /** Configures server identity (name, version, etc.). */
    public ServerBuilder info(Consumer<ServerIdentityBuilder> configurer) {
        configurer.accept(identityBuilder);
        return this;
    }

    /** Configures which MCP capabilities are enabled. */
    public ServerBuilder capabilities(Consumer<CapabilitiesConfig.Builder> configurer) {
        configurer.accept(capabilitiesConfig);
        return this;
    }

    /** Configures session lifecycle settings. */
    public ServerBuilder session(Consumer<SessionConfig.Builder> configurer) {
        configurer.accept(sessionBuilder);
        return this;
    }

    /** Configures network settings (host, port, CORS, etc.). */
    public ServerBuilder network(Consumer<NetworkConfig.Builder> configurer) {
        configurer.accept(networkBuilder);
        return this;
    }

    /** Configures handler-execution runtime settings (shutdown grace period, etc.). */
    public ServerBuilder runtime(Consumer<RuntimeConfig.Builder> configurer) {
        configurer.accept(runtimeBuilder);
        return this;
    }

    /** Configures JSON payload settings (serde, input/output schema validators). */
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

    /** Sets the server name (shorthand for {@code info(b -> b.name(name))}). */
    public ServerBuilder name(String name) {
        identityBuilder.name(name);
        return this;
    }

    /** Sets the listen port (shorthand for {@code network(b -> b.port(port))}). */
    public ServerBuilder port(int port) {
        networkBuilder.port(port);
        return this;
    }

    // === Feature registration ===

    /** Registers a tool handler. */
    public ServerBuilder tool(ToolHandler handler) {
        featuresConfig.tools.add(handler);
        return this;
    }

    /** Registers a synchronous tool handler. */
    public ServerBuilder tool(SyncToolHandler handler) {
        featuresConfig.tools.add(handler);
        return this;
    }

    /** Registers an asynchronous tool handler. */
    public ServerBuilder tool(AsyncToolHandler handler) {
        featuresConfig.tools.add(handler);
        return this;
    }

    /** Registers a tool with string JSON schemas and a handler function. */
    public ServerBuilder tool(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            java.util.function.BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        return tool(SyncToolHandler.of(name, description, inputSchemaJson, outputSchemaJson, fn));
    }

    /** Registers a resource with no read handler (returns empty content). */
    public ServerBuilder resource(ResourceDescriptor descriptor) {
        featuresConfig.resources.add(new FeaturesConfig.ResourceRegistration(descriptor, null));
        return this;
    }

    /** Registers a resource with a read handler. */
    public ServerBuilder resource(ResourceDescriptor descriptor, ResourceHandler handler) {
        featuresConfig.resources.add(new FeaturesConfig.ResourceRegistration(descriptor, handler));
        return this;
    }

    /** Registers a prompt with static messages (no handler, messages provided directly). */
    public ServerBuilder prompt(PromptDescriptor descriptor, List<PromptMessage> messages) {
        featuresConfig.prompts.add(new FeaturesConfig.PromptRegistration(descriptor, args -> messages));
        return this;
    }

    /** Registers a prompt with a dynamic handler. */
    public ServerBuilder prompt(PromptDescriptor descriptor, PromptHandler handler) {
        featuresConfig.prompts.add(new FeaturesConfig.PromptRegistration(descriptor, handler));
        return this;
    }

    /** Registers a server extension. */
    public ServerBuilder extension(ServerExtension extension) {
        featuresConfig.extensions.add(extension);
        return this;
    }

    /**
     * Sets a custom JSON schema validator for both input and output validation.
     *
     * @deprecated Use {@link #json(Consumer)} with
     * {@link JsonConfig.Builder#inputSchemaValidator(JsonSchemaValidator)} and
     * {@link JsonConfig.Builder#outputSchemaValidator(JsonSchemaValidator)} instead.
     */
    @Deprecated(forRemoval = true, since = "1.0.0-beta.4")
    public ServerBuilder jsonSchemaValidator(JsonSchemaValidator validator) {
        featuresConfig.inputSchemaValidator = validator;
        featuresConfig.outputSchemaValidator = validator;
        return this;
    }

    /**
     * Sets the payload serializer/deserializer for structured values and tool arguments.
     * Defaults to Jackson. Structured values must be types the serde understands;
     * {@code JsonNode} and {@code RawJson} values bypass it.
     */
    public ServerBuilder payloadSerde(PayloadSerde serde) {
        featuresConfig.payloadSerializer = serde;
        featuresConfig.payloadDeserializer = serde;
        return this;
    }

    /** Sets a separate validator for tool input schema validation. */
    public ServerBuilder inputSchemaValidator(JsonSchemaValidator validator) {
        featuresConfig.inputSchemaValidator = validator;
        return this;
    }

    /** Sets a separate validator for tool output schema validation. */
    public ServerBuilder outputSchemaValidator(JsonSchemaValidator validator) {
        featuresConfig.outputSchemaValidator = validator;
        return this;
    }

    /**
     * Sets a caller-owned executor for handler dispatch. The server will not shut it down on close.
     * Mutually exclusive with {@link #threadFactory}.
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

    // === Transport escape hatch ===

    /** Provides a customizer for the Netty channel pipeline. */
    public ServerBuilder pipelineCustomizer(@Nullable Consumer<ChannelPipeline> customizer) {
        this.pipelineCustomizer = customizer;
        return this;
    }

    // === Terminal methods ===

    /** Builds the {@link Server} without binding a transport. */
    public Server build() {
        var sessionConfig = sessionBuilder.build();
        var router = sessionConfig.sessionLogRouter() != null
                ? sessionConfig.sessionLogRouter()
                : new InMemorySessionLogRouter();
        var store = sessionConfig.sessionStore() != null ? sessionConfig.sessionStore() : new InMemorySessionStore();
        var allExtensions = Collections.unmodifiableList(featuresConfig.extensions);
        var serverConfig = buildConfig();
        ExecutorService resolvedExecutor;
        boolean ownsExecutor;
        if (executor != null) {
            resolvedExecutor = executor;
            ownsExecutor = false;
        } else if (threadFactory != null) {
            resolvedExecutor = Executors.newThreadPerTaskExecutor(threadFactory);
            ownsExecutor = true;
        } else {
            resolvedExecutor = Server.defaultExecutorForBuilder();
            ownsExecutor = true;
        }
        var server = new Server(
                resolvedExecutor,
                ownsExecutor,
                router,
                store,
                serverConfig,
                featuresConfig.inputSchemaValidator,
                featuresConfig.outputSchemaValidator,
                featuresConfig.payloadSerializer,
                featuresConfig.payloadDeserializer,
                allExtensions);
        featuresConfig.tools.forEach(server::registerTool);
        featuresConfig.resources.forEach(r -> {
            var d = r.descriptor();
            server.resources()
                    .add(
                            d,
                            r.handler() != null
                                    ? r.handler()
                                    : (ctx, req) -> TextResourceContents.of(d.uri(), d.mimeType(), "", null));
        });
        featuresConfig.prompts.forEach(p -> server.prompts().add(p.descriptor(), p.handler()));
        return server;
    }

    /** Builds the server and starts the Netty transport (blocking). Requires a port to be set. */
    public ServerHandle start() {
        var networkConfig = networkBuilder.build();
        if (networkConfig.port() < 0) {
            throw new IllegalStateException("Port must be set before start()");
        }
        var server = build();
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
        return new ServerHandle(server, netty.port(), netty);
    }

    /** Builds the server and starts the Netty transport (non-blocking). */
    public CompletableFuture<ServerHandle> startAsync() {
        return CompletableFuture.supplyAsync(this::start);
    }

    /** Builds the {@link ServerConfig} from the current builder state. */
    public ServerConfig buildConfig() {
        return new ServerConfig(
                identityBuilder.build(),
                capabilitiesConfig.build(),
                sessionBuilder.build(),
                networkBuilder.build(),
                runtimeBuilder.build());
    }

    static final class FeaturesConfig {

        final List<ToolHandler> tools = new ArrayList<>();
        final List<ResourceRegistration> resources = new ArrayList<>();
        final List<PromptRegistration> prompts = new ArrayList<>();
        final List<ServerExtension> extensions = new ArrayList<>();

        JsonSchemaValidator inputSchemaValidator = new NetworkntJsonSchemaValidator();
        JsonSchemaValidator outputSchemaValidator = new NetworkntJsonSchemaValidator();
        PayloadSerializer payloadSerializer = new JacksonPayloadSerde();
        PayloadDeserializer payloadDeserializer = new JacksonPayloadSerde();

        record PromptRegistration(PromptDescriptor descriptor, PromptHandler handler) {}

        record ResourceRegistration(
                ResourceDescriptor descriptor, @Nullable ResourceHandler handler) {}
    }
}
