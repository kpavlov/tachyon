/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server;

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
import io.netty.channel.ChannelPipeline;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Configures and builds a {@link TachyonServer}; call {@link TachyonServer#start()} to bind its transport. */
public interface ServerBuilder {

    /** Configures server identity. */
    ServerBuilder info(Consumer<ServerIdentity.Builder> configurer);

    /** Configures advertised MCP capabilities. */
    ServerBuilder capabilities(Consumer<CapabilitiesConfig.Builder> configurer);

    /** Configures session lifecycle and persistence. */
    ServerBuilder session(Consumer<SessionConfig.Builder> configurer);

    /** Configures the network transport. */
    ServerBuilder network(Consumer<NetworkConfig.Builder> configurer);

    /** Configures handler execution. */
    ServerBuilder runtime(Consumer<RuntimeConfig.Builder> configurer);

    /** Configures monitoring. */
    ServerBuilder monitoring(Consumer<MonitoringConfig.Builder> configurer);

    /** Configures JSON serialization and validation. */
    ServerBuilder json(Consumer<JsonConfig.Builder> configurer);

    /** Sets the server name. */
    ServerBuilder name(String name);

    /** Sets the listen port. */
    ServerBuilder port(int port);

    /** Sets the server version. */
    ServerBuilder version(String version);

    /** Sets the bind host. */
    ServerBuilder host(String host);

    /**
     * Registers tools through the server's tool façade at the end of {@link #build()}.
     */
    ServerBuilder withTools(Consumer<Tools> registrar);

    /** Registers resources through the server's resource façade at the end of {@link #build()}. */
    ServerBuilder withResources(Consumer<Resources> registrar);

    /** Registers prompts through the server's prompt façade at the end of {@link #build()}. */
    ServerBuilder withPrompts(Consumer<Prompts> registrar);

    /** Registers completions through the server's completion façade at the end of {@link #build()}. */
    ServerBuilder withCompletions(Consumer<Completions> registrar);

    /** Registers a server extension. */
    ServerBuilder extension(ServerExtension extension);

    /**
     * Sets a caller-owned thread-per-task executor. The caller must close the executor after the
     * server is closed. {@link #build()} rejects bounded executors with an
     * {@link IllegalArgumentException}.
     */
    ServerBuilder executor(ExecutorService executor);

    /** Sets the thread factory used by the server-owned thread-per-task executor. */
    ServerBuilder threadFactory(ThreadFactory threadFactory);

    /** Customizes each Netty channel pipeline. */
    ServerBuilder pipelineCustomizer(@Nullable Consumer<ChannelPipeline> customizer);

    /**
     * Constructs the configured server without starting its transport, then executes the configured
     * feature-registration callbacks.
     *
     * @throws IllegalArgumentException if the configured executor is bounded
     */
    TachyonServer build();

    /** Builds the immutable server configuration. */
    ServerConfig buildConfig();
}
