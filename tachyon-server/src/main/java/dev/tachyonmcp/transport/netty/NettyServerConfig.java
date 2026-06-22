/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public record NettyServerConfig(
        String host,
        int port,
        String endpointPath,
        Duration readerIdleTimeout,
        Duration writerIdleTimeout,
        int maxContentLength,
        @Nullable CorsConfig corsConfig,
        @Nullable Consumer<ChannelPipeline> pipelineCustomizer) {

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
        return new NettyServerConfig(
                "127.0.0.1",
                port,
                "/mcp",
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH,
                buildCorsConfig(null, false, false, null),
                null);
    }

    static NettyServerConfig defaults(String host, int port) {
        return new NettyServerConfig(
                host,
                port,
                "/mcp",
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH,
                buildCorsConfig(null, false, false, null),
                null);
    }
}
