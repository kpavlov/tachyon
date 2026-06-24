/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.transport.netty.McpChannelInitializer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record NetworkConfig(
        String host,
        int port,
        String endpointPath,
        Duration readerIdleTimeout,
        Duration writerIdleTimeout,
        int maxContentLength,
        @Nullable List<String> allowedOrigins,
        boolean allowNullOrigin,
        boolean allowPrivateNetworks,
        @Nullable List<String> allowedHeaders) {

    public static final NetworkConfig DEFAULT = new NetworkConfig(
            "127.0.0.1",
            -1,
            "/mcp",
            Duration.ofSeconds(60),
            Duration.ofMinutes(5),
            McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH,
            null,
            false,
            false,
            null);

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host = "127.0.0.1";
        private int port = -1;
        private String endpointPath = "/mcp";
        private Duration readerIdleTimeout = Duration.ofSeconds(60);
        private Duration writerIdleTimeout = Duration.ofMinutes(5);
        private int maxContentLength = McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH;
        private @Nullable List<String> allowedOrigins;
        private boolean allowNullOrigin;
        private boolean allowPrivateNetworks;
        private @Nullable List<String> allowedHeaders;
        private boolean hostPortExplicitlySet;
        private boolean addressExplicitlySet;

        private Builder() {}

        public Builder host(String host) {
            if (addressExplicitlySet) {
                throw new IllegalStateException("Cannot combine host() with address()");
            }
            this.host = host;
            this.hostPortExplicitlySet = true;
            return this;
        }

        public Builder port(int port) {
            if (addressExplicitlySet) {
                throw new IllegalStateException("Cannot combine port() with address()");
            }
            this.port = port;
            this.hostPortExplicitlySet = true;
            return this;
        }

        public Builder address(SocketAddress addr) {
            if (hostPortExplicitlySet) {
                throw new IllegalStateException("Cannot combine address() with host()/port()");
            }
            if (addr instanceof InetSocketAddress inet) {
                this.host = inet.getHostString();
                this.port = inet.getPort();
            }
            this.addressExplicitlySet = true;
            return this;
        }

        public Builder endpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
            return this;
        }

        public Builder readerIdleTimeout(Duration timeout) {
            this.readerIdleTimeout = timeout;
            return this;
        }

        public Builder writerIdleTimeout(Duration timeout) {
            this.writerIdleTimeout = timeout;
            return this;
        }

        public Builder maxContentLength(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("maxContentLength must be positive");
            }
            this.maxContentLength = bytes;
            return this;
        }

        public Builder allowedOrigins(String... origins) {
            this.allowedOrigins = List.of(origins);
            return this;
        }

        public Builder allowNullOrigin(boolean allow) {
            this.allowNullOrigin = allow;
            return this;
        }

        public Builder allowPrivateNetworks(boolean allow) {
            this.allowPrivateNetworks = allow;
            return this;
        }

        public Builder allowedHeaders(String... headers) {
            this.allowedHeaders = List.of(headers);
            return this;
        }

        public NetworkConfig build() {
            return new NetworkConfig(
                    host,
                    port,
                    endpointPath,
                    readerIdleTimeout,
                    writerIdleTimeout,
                    maxContentLength,
                    allowedOrigins,
                    allowNullOrigin,
                    allowPrivateNetworks,
                    allowedHeaders);
        }
    }
}
