/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.server.RpcDispatcher;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.transport.netty.http.AcceptValidationHandler;
import dev.tachyonmcp.transport.netty.http.DnsRebindingProtectionHandler;
import dev.tachyonmcp.transport.netty.http.EndpointValidatorHandler;
import dev.tachyonmcp.transport.netty.http.StatelessValidatorHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
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

    private static final String CHANNEL_LOGGER_NAME = "me.kpavlov.tachyon.transport.netty.channel";
    private static final LoggingHandler CHANNEL_LOGGER = new LoggingHandler(CHANNEL_LOGGER_NAME, LogLevel.DEBUG);
    private static final boolean CHANNEL_LOGGING_ENABLED =
            org.slf4j.LoggerFactory.getLogger(CHANNEL_LOGGER_NAME).isDebugEnabled();
    private static final int FLUSH_AFTER = FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES;

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

    private final ServerEngine server;
    private final RpcDispatcher dispatcher;

    @Nullable
    private final Consumer<ChannelPipeline> pipelineCustomizer;

    private final ChannelGroup childChannels;

    public McpChannelInitializer(
            String endpointPath,
            boolean stateless,
            ServerEngine server,
            Duration readerIdleTimeout,
            Duration writerIdleTimeout,
            int maxContentLength,
            ChannelGroup childChannels,
            @Nullable CorsConfig corsConfig,
            @Nullable Consumer<ChannelPipeline> pipelineCustomizer) {
        this.stateless = stateless;
        this.server = server;
        this.readerIdleTimeout = readerIdleTimeout;
        this.writerIdleTimeout = writerIdleTimeout;
        this.maxContentLength = maxContentLength;
        this.corsConfig = corsConfig;
        this.pipelineCustomizer = pipelineCustomizer;
        this.childChannels = childChannels;
        this.dispatcher = new RpcDispatcher(server, server.executor());
        this.interactionHandler = new InteractionHandler();

        protocolVersionHandler = new ProtocolVersionHandler(endpointPath);
        acceptHeaderValidator = new AcceptValidationHandler(endpointPath);
        endpointValidatorHandler = new EndpointValidatorHandler(endpointPath);
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        childChannels.add(ch);
        final var p = ch.pipeline();
        p.addFirst("flush", new FlushConsolidationHandler(FLUSH_AFTER, true));
        if (CHANNEL_LOGGING_ENABLED) {
            p.addLast("logger", CHANNEL_LOGGER);
        }
        p.addLast("http", new HttpServerCodec());

        // SessionTouchHandler is installed lazily at session-bind time (see SessionTouchHandler#install).
        // During initialization, no session is bound to the channel, so no touch is needed.

        // Idiomatic keep-alive management: inspects each request's Connection intent and either
        // keeps the socket alive or appends `Connection: close` and closes after the final content.
        // Placed right after the codec so it governs responses from ALL downstream handlers
        // (validation handlers write before the aggregator; protocol handlers write after it).
        p.addLast("http-keep-alive", new HttpServerKeepAliveHandler());
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

        // HttpObjectAggregator owns `Expect: 100-continue`: it answers 100 Continue for
        // acceptable requests and rejects oversized ones (413/417) before the body is
        // transferred. A separate HttpServerExpectContinueHandler would defeat that by
        // always acking 100 Continue upstream of the aggregator.
        p.addLast("http-aggregator", new HttpObjectAggregator(maxContentLength));
        // On a plain HTTP keep-alive socket an idle tick closes the connection. On a channel carrying
        // an open SSE stream the SseHeartbeat scheduler drives heartbeats independently, so idle ticks
        // are a no-op for SSE channels. Lower readerIdleTimeout below any intermediary proxy's idle
        // timeout (commonly 60s) to keep non-SSE keep-alive sockets from being reaped.
        if (!readerIdleTimeout.isZero() || !writerIdleTimeout.isZero()) {
            p.addLast(
                    "idle",
                    new IdleStateHandler(
                            readerIdleTimeout.toMillis(), writerIdleTimeout.toMillis(), 0, TimeUnit.MILLISECONDS));
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
