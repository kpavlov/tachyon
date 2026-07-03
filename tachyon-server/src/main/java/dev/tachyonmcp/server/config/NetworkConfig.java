/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.transport.netty.McpChannelInitializer;
import dev.tachyonmcp.transport.netty.NettyIoEngine;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Network-level server configuration.
 *
 * @param host               bind address (default {@code "127.0.0.1"})
 * @param port               listen port (must be set before {@code bind()})
 * @param endpointPath       HTTP path for MCP endpoints (default {@code "/mcp"})
 * @param readerIdleTimeout  idle timeout for reading (default 60s)
 * @param writerIdleTimeout  idle timeout for writing (default 5min)
 * @param maxContentLength   maximum HTTP body size in bytes
 * @param allowedOrigins     CORS allowed origins ({@code null} = defaults)
 * @param allowNullOrigin    whether to allow {@code Origin: null}
 * @param allowPrivateNetworks whether to allow private network CORS
 * @param allowedHeaders     additional allowed CORS headers
 * @param ioEngine           Netty I/O engine; defaults to {@link NettyIoEngine#AUTO}
 */
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
        @Nullable List<String> allowedHeaders,
        NettyIoEngine ioEngine) {

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
            null,
            NettyIoEngine.AUTO);

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link NetworkConfig}. */
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
        private NettyIoEngine ioEngine = NettyIoEngine.AUTO;
        private boolean hostPortExplicitlySet;
        private boolean addressExplicitlySet;

        private Builder() {}

        /** Sets the bind address. Mutually exclusive with {@link #address}. */
        public Builder host(String host) {
            if (addressExplicitlySet) {
                throw new IllegalStateException("Cannot combine host() with address()");
            }
            this.host = host;
            this.hostPortExplicitlySet = true;
            return this;
        }

        /** Sets the listen port. Mutually exclusive with {@link #address}. */
        public Builder port(int port) {
            if (addressExplicitlySet) {
                throw new IllegalStateException("Cannot combine port() with address()");
            }
            this.port = port;
            this.hostPortExplicitlySet = true;
            return this;
        }

        /** Sets the bind address and port from a {@link SocketAddress}. Mutually exclusive with {@link #host}/{@link #port}. */
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

        /** Sets the HTTP path for MCP endpoints. */
        public Builder endpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
            return this;
        }

        /** Sets the reader idle timeout. */
        public Builder readerIdleTimeout(Duration timeout) {
            this.readerIdleTimeout = timeout;
            return this;
        }

        /** Sets the writer idle timeout. */
        public Builder writerIdleTimeout(Duration timeout) {
            this.writerIdleTimeout = timeout;
            return this;
        }

        /** Sets the maximum HTTP body size in bytes (must be positive). */
        public Builder maxContentLength(int bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("maxContentLength must be positive");
            }
            this.maxContentLength = bytes;
            return this;
        }

        /** Sets the CORS allowed origins. */
        public Builder allowedOrigins(String... origins) {
            this.allowedOrigins = List.of(origins);
            return this;
        }

        /** Sets whether to allow {@code Origin: null}. */
        public Builder allowNullOrigin(boolean allow) {
            this.allowNullOrigin = allow;
            return this;
        }

        /** Sets whether to allow private network CORS. */
        public Builder allowPrivateNetworks(boolean allow) {
            this.allowPrivateNetworks = allow;
            return this;
        }

        /** Sets additional allowed CORS headers. */
        public Builder allowedHeaders(String... headers) {
            this.allowedHeaders = List.of(headers);
            return this;
        }

        /** Sets the Netty I/O engine; defaults to {@link NettyIoEngine#AUTO}. */
        public Builder ioEngine(NettyIoEngine ioEngine) {
            this.ioEngine = Objects.requireNonNull(ioEngine, "ioEngine");
            return this;
        }

        /** Builds the {@link NetworkConfig}. */
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
                    allowedHeaders,
                    ioEngine);
        }
    }
}
