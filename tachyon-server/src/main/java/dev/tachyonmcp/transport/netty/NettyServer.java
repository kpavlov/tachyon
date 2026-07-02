/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.server.Server;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.Closeable;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty-based MCP server with Streamable HTTP transport. Detects the best
 * available I/O handler (io_uring, epoll, kqueue, NIO) and binds a
 * {@link ServerBootstrap} with the MCP pipeline defined by
 * {@link McpChannelInitializer}.
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
        var transport = Transport.detect();
        logger.info("Netty transport: {}", transport.channel.getSimpleName());

        // Event loops run on PLATFORM threads. Netty I/O loops never voluntarily
        // yield (they spin in epoll_wait / io_uring_enter / Selector.select), and
        // native transports pin via JNI — so virtual threads provide no benefit
        // here and add scheduling cost. Virtual threads are used only for
        // application-level work (see Server#executor()).
        eventLoopGroup = new MultiThreadIoEventLoopGroup(new DefaultThreadFactory("netty-io"), transport.ioHandler);

        var bootstrap = new ServerBootstrap();
        bootstrap
                .group(eventLoopGroup)
                .channel(transport.channel)
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
            logger.info("NettyServer started on {}", serverChannel.localAddress());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to start Netty server on " + config.host() + ":" + config.port(), e);
        }
    }

    private record Transport(IoHandlerFactory ioHandler, Class<? extends ServerSocketChannel> channel) {
        static Transport detect() {
            if (isIoUringAvailable()) {
                return new Transport(IoUringIoHandler.newFactory(), IoUringServerSocketChannel.class);
            }
            if (Epoll.isAvailable()) {
                return new Transport(EpollIoHandler.newFactory(), EpollServerSocketChannel.class);
            }
            if (KQueue.isAvailable()) {
                return new Transport(KQueueIoHandler.newFactory(), KQueueServerSocketChannel.class);
            }
            return new Transport(NioIoHandler.newFactory(), NioServerSocketChannel.class);
        }
    }

    private static boolean isIoUringAvailable() {
        try {
            return IoUring.isAvailable();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    @Override
    public void close() {
        logger.debug("Shutting down NettyServer");
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
