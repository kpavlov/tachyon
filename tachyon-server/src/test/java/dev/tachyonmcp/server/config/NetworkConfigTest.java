/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.transport.netty.McpChannelInitializer;
import dev.tachyonmcp.transport.netty.NettyIoEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link NetworkConfig} defaults, builder validation, and immutability of collection
 * fields — a built config must never expose a mutable collection to callers.
 *
 * @author Konstantin Pavlov
 */
class NetworkConfigTest {

    @Test
    void usesDefaults() {
        var config = NetworkConfig.builder().build();

        assertThat(config.host()).isEqualTo("127.0.0.1");
        assertThat(config.port()).isEqualTo(-1);
        assertThat(config.endpointPath()).isEqualTo("/mcp");
        assertThat(config.readerIdleTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.writerIdleTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.maxContentLength()).isEqualTo(McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH);
        assertThat(config.allowedOrigins()).isNull();
        assertThat(config.allowNullOrigin()).isFalse();
        assertThat(config.allowPrivateNetworks()).isFalse();
        assertThat(config.allowedHeaders()).isNull();
        assertThat(config.ioEngine()).isEqualTo(NettyIoEngine.AUTO);
        assertThat(config.heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void allowedOriginsViaBuilderIsUnmodifiable() {
        var config = NetworkConfig.builder()
                .allowedOrigins("http://localhost", "http://127.0.0.1")
                .build();

        assertThat(config.allowedOrigins()).containsExactly("http://localhost", "http://127.0.0.1");
        assertThatThrownBy(() -> config.allowedOrigins().add("http://evil.com"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allowedHeadersViaBuilderIsUnmodifiable() {
        var config = NetworkConfig.builder().allowedHeaders("X-Custom").build();

        assertThat(config.allowedHeaders()).containsExactly("X-Custom");
        assertThatThrownBy(() -> config.allowedHeaders().add("X-Evil"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void directConstructionWithMutableListIsDefended() {
        var origins = new ArrayList<>(List.of("http://example.com"));
        var headers = new ArrayList<>(List.of("X-Foo"));
        var config = new NetworkConfig(
                "127.0.0.1",
                8080,
                "/mcp",
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH,
                origins,
                false,
                false,
                headers,
                NettyIoEngine.AUTO,
                Duration.ofSeconds(15));

        // Mutating the original lists must not affect the config
        origins.add("http://evil.com");
        headers.add("X-Evil");

        assertThat(config.allowedOrigins()).containsExactly("http://example.com");
        assertThat(config.allowedHeaders()).containsExactly("X-Foo");
    }

    @Test
    void rejectsNonPositiveMaxContentLength() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NetworkConfig.builder().maxContentLength(0))
                .withMessage("maxContentLength must be positive");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> NetworkConfig.builder().maxContentLength(-1))
                .withMessage("maxContentLength must be positive");
    }

    @Test
    void buildsDistinctConfigs() {
        var config1 = NetworkConfig.builder().port(8080).build();
        var config2 = NetworkConfig.builder().port(9090).build();

        assertThat(config1).isNotSameAs(config2);
        assertThat(config1.port()).isEqualTo(8080);
        assertThat(config2.port()).isEqualTo(9090);
    }
}
