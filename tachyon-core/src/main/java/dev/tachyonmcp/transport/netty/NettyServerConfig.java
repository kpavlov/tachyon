/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.server.config.NetworkConfig;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the Netty transport.
 *
 * @param host               the host address to bind to
 * @param port               the port to bind to
 * @param endpointPath       the HTTP endpoint path for MCP messages
 * @param readerIdleTimeout  idle timeout for reading
 * @param writerIdleTimeout  idle timeout for writing
 * @param maxContentLength   maximum HTTP content length in bytes
 * @param corsConfig         CORS configuration, or {@code null} for defaults
 * @param ioEngine           the Netty I/O engine to use
 * @param pipelineCustomizer optional customizer for the Netty channel pipeline
 */
public record NettyServerConfig(
        String host,
        int port,
        String endpointPath,
        Duration readerIdleTimeout,
        Duration writerIdleTimeout,
        int maxContentLength,
        @Nullable CorsConfig corsConfig,
        NettyIoEngine ioEngine,
        @Nullable Consumer<ChannelPipeline> pipelineCustomizer) {

    /** Builds a CORS configuration from the given parameters. */
    public static CorsConfig buildCorsConfig(
            @Nullable List<String> allowedOrigins,
            boolean allowNullOrigin,
            boolean allowPrivateNetworks,
            @Nullable List<String> allowedHeaders) {
        var builder = allowedOrigins != null
                ? CorsConfigBuilder.forOrigins(allowedOrigins.toArray(String[]::new))
                : CorsConfigBuilder.forOrigins("http://localhost", "http://127.0.0.1");
        if (allowNullOrigin) {
            builder.allowNullOrigin();
        }
        if (allowPrivateNetworks) {
            builder.allowPrivateNetwork();
        }
        if (allowedHeaders != null && !allowedHeaders.isEmpty()) {
            builder.allowedRequestHeaders(allowedHeaders.toArray(String[]::new));
        }
        return builder.build();
    }

    static NettyServerConfig defaults(int port) {
        return defaults(NetworkConfig.DEFAULT_HOST, port);
    }

    static NettyServerConfig defaults(String host, int port) {
        return new NettyServerConfig(
                host,
                port,
                NetworkConfig.DEFAULT_ENDPOINT_PATH,
                NetworkConfig.DEFAULT_READER_IDLE_TIMEOUT,
                NetworkConfig.DEFAULT_WRITER_IDLE_TIMEOUT,
                McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH,
                buildCorsConfig(null, false, false, null),
                NettyIoEngine.AUTO,
                null);
    }
}
