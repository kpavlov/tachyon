/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server;

import dev.tachyonmcp.json.JsonSchemaValidator;
import dev.tachyonmcp.json.PayloadDeserializer;
import dev.tachyonmcp.json.PayloadSerializer;
import dev.tachyonmcp.json.spi.JsonSchemaFactory;
import dev.tachyonmcp.server.config.CapabilitiesConfig;
import dev.tachyonmcp.server.config.JsonConfig;
import dev.tachyonmcp.server.config.MonitoringConfig;
import dev.tachyonmcp.server.config.NetworkConfig;
import dev.tachyonmcp.server.config.RuntimeConfig;
import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.config.ServerIdentity;
import dev.tachyonmcp.server.config.SessionConfig;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.completions.Completions;
import dev.tachyonmcp.server.features.prompts.Prompts;
import dev.tachyonmcp.server.features.resources.Resources;
import dev.tachyonmcp.server.features.tools.Tools;
import dev.tachyonmcp.server.json.JacksonPayloadSerde;
import dev.tachyonmcp.server.json.NetworkntJsonSchemaValidator;
import dev.tachyonmcp.server.session.InMemorySessionEventStore;
import dev.tachyonmcp.server.session.InMemorySessionStore;
import io.netty.channel.ChannelPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for {@link TachyonServer} configuration.
 */
final class DefaultServerBuilder implements ServerBuilder {

    private final ServerIdentity.Builder identityBuilder = ServerIdentity.builder();
    private final CapabilitiesConfig.Builder capabilitiesConfig = CapabilitiesConfig.builder();
    private final SessionConfig.Builder sessionBuilder = SessionConfig.builder();
    private final NetworkConfig.Builder networkBuilder = NetworkConfig.builder();
    private final RuntimeConfig.Builder runtimeBuilder = RuntimeConfig.builder();
    private final MonitoringConfig.Builder monitoringBuilder = MonitoringConfig.builder();
    private final List<ServerExtension> extensions = new ArrayList<>();
    private final List<Consumer<TachyonServer>> bootstrapRegistrations = new ArrayList<>();

    private JsonSchemaValidator inputSchemaValidator = new NetworkntJsonSchemaValidator();
    private JsonSchemaValidator outputSchemaValidator = new NetworkntJsonSchemaValidator();
    private PayloadSerializer payloadSerializer = new JacksonPayloadSerde();
    private PayloadDeserializer payloadDeserializer = new JacksonPayloadSerde();
    private @Nullable JsonSchemaFactory<String> schemaFactory;

    @Nullable
    private Consumer<ChannelPipeline> pipelineCustomizer;

    private @Nullable ExecutorService executor;
    private @Nullable ThreadFactory threadFactory;

    DefaultServerBuilder() {}

    // === Configuration groups ===

    /**
     * Configures server identity (name, version, etc.).
     */
    @Override
    public ServerBuilder info(Consumer<ServerIdentity.Builder> configurer) {
        configurer.accept(identityBuilder);
        return this;
    }

    /**
     * Configures which MCP capabilities are enabled.
     */
    @Override
    public ServerBuilder capabilities(Consumer<CapabilitiesConfig.Builder> configurer) {
        configurer.accept(capabilitiesConfig);
        return this;
    }

    /**
     * Configures session lifecycle settings.
     */
    @Override
    public ServerBuilder session(Consumer<SessionConfig.Builder> configurer) {
        configurer.accept(sessionBuilder);
        return this;
    }

    /**
     * Configures network settings (host, port, CORS, etc.).
     */
    @Override
    public ServerBuilder network(Consumer<NetworkConfig.Builder> configurer) {
        configurer.accept(networkBuilder);
        return this;
    }

    /**
     * Configures handler-execution runtime settings (shutdown grace period, etc.).
     */
    @Override
    public ServerBuilder runtime(Consumer<RuntimeConfig.Builder> configurer) {
        configurer.accept(runtimeBuilder);
        return this;
    }

    /**
     * Configures diagnostics and observability settings (slow-request logging, etc.).
     */
    @Override
    public ServerBuilder monitoring(Consumer<MonitoringConfig.Builder> configurer) {
        configurer.accept(monitoringBuilder);
        return this;
    }

    /**
     * Configures JSON payload settings (serde, input/output schema validators).
     */
    @Override
    public ServerBuilder json(Consumer<JsonConfig.Builder> configurer) {
        var builder = JsonConfig.builder();
        configurer.accept(builder);
        var config = builder.build();
        if (config.serializer() != null) {
            payloadSerializer = config.serializer();
        }
        if (config.deserializer() != null) {
            payloadDeserializer = config.deserializer();
        }
        if (config.inputValidator() != null) {
            inputSchemaValidator = config.inputValidator();
        }
        if (config.outputValidator() != null) {
            outputSchemaValidator = config.outputValidator();
        }
        if (config.schemaFactory() != null) {
            schemaFactory = config.schemaFactory();
        }
        return this;
    }

    /**
     * Sets the server name (shorthand for {@code info(b -> b.name(name))}).
     */
    @Override
    public ServerBuilder name(String name) {
        identityBuilder.name(name);
        return this;
    }

    /**
     * Sets the listen port (shorthand for {@code network(b -> b.port(port))}).
     */
    @Override
    public ServerBuilder port(int port) {
        networkBuilder.port(port);
        return this;
    }

    /**
     * Sets the server version (shorthand for {@code info(b -> b.version(version))}).
     */
    @Override
    public ServerBuilder version(String version) {
        identityBuilder.version(version);
        return this;
    }

    /**
     * Sets the bind address (shorthand for {@code network(b -> b.host(host))}).
     */
    @Override
    public ServerBuilder host(String host) {
        networkBuilder.host(host);
        return this;
    }

    /**
     * Registers tools through the server's tool façade after construction.
     *
     * @param registrar tool registrations to apply before this builder returns the server
     * @return this builder
     */
    @Override
    public ServerBuilder withTools(Consumer<Tools> registrar) {
        bootstrapRegistrations.add(server -> registrar.accept(server.tools()));
        return this;
    }

    /**
     * Registers resources through the server's resource façade after construction.
     *
     * @param registrar resource registrations to apply before this builder returns the server
     * @return this builder
     */
    @Override
    public ServerBuilder withResources(Consumer<Resources> registrar) {
        bootstrapRegistrations.add(server -> registrar.accept(server.resources()));
        return this;
    }

    /**
     * Registers prompts through the server's prompt façade after construction.
     *
     * @param registrar prompt registrations to apply before this builder returns the server
     * @return this builder
     */
    @Override
    public ServerBuilder withPrompts(Consumer<Prompts> registrar) {
        bootstrapRegistrations.add(server -> registrar.accept(server.prompts()));
        return this;
    }

    /**
     * Registers completions through the server's completion façade after construction.
     *
     * @param registrar completion registrations to apply before this builder returns the server
     * @return this builder
     */
    @Override
    public ServerBuilder withCompletions(Consumer<Completions> registrar) {
        bootstrapRegistrations.add(server -> registrar.accept(server.completions()));
        return this;
    }

    /**
     * Registers a server extension.
     */
    @Override
    public ServerBuilder extension(ServerExtension extension) {
        extensions.add(extension);
        return this;
    }

    /**
     * Sets a caller-owned executor for handler dispatch. The server will not shut it down on close.
     * Must be thread-per-task (each task starts on a new thread); bounded pools deadlock with
     * the blocking-first dispatch contract. Mutually exclusive with {@link #threadFactory}.
     */
    @Override
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
    @Override
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
    @Override
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
     * only after {@link TachyonServer#start()}.
     *
     * @return the configured server
     */
    @Override
    public TachyonServer build() {
        var sessionConfig = sessionBuilder.build();
        var sessionEventStore = sessionConfig.sessionEventStore() != null
                ? sessionConfig.sessionEventStore()
                : new InMemorySessionEventStore();
        var store = sessionConfig.sessionStore() != null ? sessionConfig.sessionStore() : new InMemorySessionStore();
        var allExtensions = List.copyOf(extensions);
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
                inputSchemaValidator,
                outputSchemaValidator,
                payloadSerializer,
                payloadDeserializer,
                schemaFactory,
                allExtensions,
                pipelineCustomizer);
        bootstrapRegistrations.forEach(registrar -> registrar.accept(server));
        return server;
    }

    /**
     * Builds the {@link ServerConfig} from the current builder state.
     */
    @Override
    public ServerConfig buildConfig() {
        return new ServerConfig(
                identityBuilder.build(),
                capabilitiesConfig.build(),
                sessionBuilder.build(),
                networkBuilder.build(),
                runtimeBuilder.build(),
                monitoringBuilder.build());
    }
}
