/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.transport.netty.http.AcceptValidationHandler;
import dev.tachyonmcp.transport.netty.http.DnsRebindingProtectionHandler;
import dev.tachyonmcp.transport.netty.http.EndpointValidatorHandler;
import dev.tachyonmcp.transport.netty.http.StatelessValidatorHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.time.Duration;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Assembles the Netty pipeline for each new MCP channel: validation handlers
 * (endpoint, origin, protocol version, accept header, stateless guard),
 * {@link InteractionHandler}, HTTP aggregation, idle timeout, the
 * initialization-phase handler ({@link McpInitializationHandler}), and the
 * {@link LifecyclePipelineCoordinator}.
 *
 * <p>In stateless mode, {@link StatelessValidatorHandler} is added to reject
 * session-related headers and DELETE methods before they reach protocol handlers.
 */
@ChannelHandler.Sharable
public class McpChannelInitializer extends ChannelInitializer<SocketChannel> {

    /** Default max aggregated request body. 64 KB was too small for schemas + tool results. */
    public static final int DEFAULT_MAX_CONTENT_LENGTH = 1024 * 1024; // 1 MB

    private static final LoggingHandler CHANNEL_LOGGER =
            new LoggingHandler("me.kpavlov.tachyon.transport.netty.channel", LogLevel.DEBUG);
    public static final int flushAfter = FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES;

    private final Duration readerIdleTimeout;
    private final Duration writerIdleTimeout;
    private final int maxContentLength;
    private final ProtocolVersionHandler protocolVersionHandler;
    private final AcceptValidationHandler acceptHeaderValidator;
    private final boolean stateless;
    private final EndpointValidatorHandler endpointValidatorHandler;
    private final InteractionHandler interactionHandler;
    private static final DnsRebindingProtectionHandler DNS_REBINDING_HANDLER = new DnsRebindingProtectionHandler();

    @Nullable
    private final CorsConfig corsConfig;

    private static final ChannelHandler statelessValidator = new StatelessValidatorHandler();

    private final McpServer server;
    private final McpDispatcher dispatcher;

    @Nullable
    private final Consumer<ChannelPipeline> pipelineCustomizer;

    public McpChannelInitializer(
            String endpointPath,
            boolean stateless,
            McpServer server,
            Duration readerIdleTimeout,
            Duration writerIdleTimeout,
            int maxContentLength,
            @Nullable CorsConfig corsConfig,
            @Nullable Consumer<ChannelPipeline> pipelineCustomizer) {
        this.stateless = stateless;
        this.server = server;
        this.readerIdleTimeout = readerIdleTimeout;
        this.writerIdleTimeout = writerIdleTimeout;
        this.maxContentLength = maxContentLength;
        this.corsConfig = corsConfig;
        this.pipelineCustomizer = pipelineCustomizer;
        this.dispatcher = new McpDispatcher(server, server.executor());
        this.interactionHandler = new InteractionHandler("mcp");

        protocolVersionHandler = new ProtocolVersionHandler(endpointPath);
        acceptHeaderValidator = new AcceptValidationHandler(endpointPath);
        endpointValidatorHandler = new EndpointValidatorHandler(endpointPath);
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        final var p = ch.pipeline();
        p.addFirst("flush", new FlushConsolidationHandler(flushAfter, true));
        p.addLast("logger", CHANNEL_LOGGER);
        p.addLast("http", new HttpServerCodec());
        p.addLast("dns-rebinding", DNS_REBINDING_HANDLER);
        if (corsConfig != null) {
            p.addLast("cors", new CorsHandler(corsConfig));
        }
        p.addLast("mcp-endpoint", endpointValidatorHandler);
        p.addLast("protocol-version", protocolVersionHandler);
        p.addLast("accept-header", acceptHeaderValidator);
        if (stateless) {
            p.addLast("stateless-mcp", statelessValidator);
        }
        p.addLast("interaction", interactionHandler);

        // Reject oversized bodies announced via `Expect: 100-continue` before they are
        // transferred, instead of buffering and then failing in the aggregator.
        p.addLast("expect-continue", new HttpServerExpectContinueHandler());
        p.addLast("http-aggregator", new HttpObjectAggregator(maxContentLength));
        if (!readerIdleTimeout.isZero() || !writerIdleTimeout.isZero()) {
            p.addLast(
                    "idle",
                    new IdleStateHandler((int) readerIdleTimeout.toSeconds(), (int) writerIdleTimeout.toSeconds(), 0));
        }

        // Both stateless and stateful modes go through the initialization phase handler.
        // It negotiates the protocol, fires InteractionEvent.OperationStarted, then the
        // LifecyclePipelineCoordinator replaces it with McpOperationHandler.
        var manager = new McpHandlerManager(server, dispatcher);
        p.addLast(manager.initHandlerName(), new McpInitializationHandler(server, dispatcher, server.executor()));
        p.addLast("lifecycle", new LifecyclePipelineCoordinator(manager));

        if (pipelineCustomizer != null) {
            pipelineCustomizer.accept(p);
        }
    }
}
