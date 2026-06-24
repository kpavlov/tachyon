/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.transport.netty.NettyServer;
import dev.tachyonmcp.transport.netty.NettyServerConfig;

public final class TachyonMcpServer {

    private TachyonMcpServer() {}

    public static ServerBuilder builder() {
        return new ServerBuilder();
    }

    public static McpServerHandle bind(ServerConfig config) {
        if (config.network().port() < 0) {
            throw new IllegalStateException("Port must be set before bind()");
        }
        var router = config.session().sessionLogRouter() != null
                ? config.session().sessionLogRouter()
                : new dev.tachyonmcp.server.session.InMemorySessionLogRouter();
        var store = config.session().sessionStore() != null
                ? config.session().sessionStore()
                : new dev.tachyonmcp.server.session.InMemorySessionStore();
        var server = new McpServer(
                router, store, config, dev.tachyonmcp.server.JsonSchemaValidator.noop(), java.util.List.of());
        var nettyConfig = new NettyServerConfig(
                config.network().host(),
                config.network().port(),
                config.network().endpointPath(),
                config.network().readerIdleTimeout(),
                config.network().writerIdleTimeout(),
                config.network().maxContentLength(),
                NettyServerConfig.buildCorsConfig(
                        config.network().allowedOrigins(),
                        config.network().allowNullOrigin(),
                        config.network().allowPrivateNetworks(),
                        config.network().allowedHeaders()),
                null);
        var netty = new NettyServer(server, nettyConfig);
        return new McpServerHandle(server, netty.port(), netty);
    }
}
