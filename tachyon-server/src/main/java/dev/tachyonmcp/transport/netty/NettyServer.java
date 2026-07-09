/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.server.Server;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.Closeable;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty-based MCP server with Streamable HTTP transport. Detects the best
 * available I/O transport (io_uring, epoll, kqueue, NIO) via
 * {@link NettyIoEngine#detect()} and binds a {@link ServerBootstrap} with the
 * MCP pipeline defined by {@link McpChannelInitializer}.
 */
public final class NettyServer implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    final MultiThreadIoEventLoopGroup eventLoopGroup;
    private final Channel serverChannel;
    private final DefaultChannelGroup childChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /**
     * Returns the port the server is bound to.
     */
    public int port() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public NettyServer(int port, Server server) {
        this(server, NettyServerConfig.defaults(port));
    }

    public NettyServer(String host, int port, Server server) {
        this(server, NettyServerConfig.defaults(host, port));
    }

    public NettyServer(Server server, NettyServerConfig config) {
        var engine = config.ioEngine();
        if (engine == NettyIoEngine.AUTO) {
            engine = NettyIoEngine.detect();
        }
        logger.info("Netty I/O engine: {}", engine);

        // Event loops run on PLATFORM threads. Netty I/O loops never voluntarily
        // yield (they spin in epoll_wait / io_uring_enter / Selector.select), and
        // native transports pin via JNI — so virtual threads provide no benefit
        // here and add scheduling cost. Virtual threads are used only for
        // application-level work (see Server#executor()).
        eventLoopGroup = new MultiThreadIoEventLoopGroup(new DefaultThreadFactory("netty-io"), engine.ioHandler());

        var bootstrap = new ServerBootstrap();
        bootstrap
                .group(eventLoopGroup)
                .channel(engine.channel())
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024, 128 * 1024))
                .childHandler(new McpChannelInitializer(
                        config.endpointPath(),
                        server.isStateless(),
                        server,
                        config.readerIdleTimeout(),
                        config.writerIdleTimeout(),
                        config.maxContentLength(),
                        childChannels,
                        config.corsConfig(),
                        config.pipelineCustomizer()));

        try {
            this.serverChannel =
                    bootstrap.bind(config.host(), config.port()).sync().channel();
            logger.info("TachyonMCP Server started on {}", serverChannel.localAddress());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to start Netty server on " + config.host() + ":" + config.port(), e);
        }
    }

    /**
     * Stops accepting new connections by closing the server channel. Existing child channels and
     * event loops stay alive so in-flight requests can complete and flush. Idempotent;
     * {@link #close()} finishes the teardown.
     */
    public void stopAccepting() {
        try {
            serverChannel.close().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        logger.debug("Shutting down TachyonMCP Server");
        try {
            serverChannel.close().sync();
            childChannels.close().sync();
            eventLoopGroup
                    .shutdownGracefully(0, 3, java.util.concurrent.TimeUnit.SECONDS)
                    .sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
