/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.protocol.ContextProvider;
import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.runtime.InteractionContext.Lifecycle;
import dev.tachyonmcp.runtime.InteractionEvent;
import dev.tachyonmcp.runtime.Session;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and manages the per-channel {@link InteractionContext} on channel
 * active/inactive lifecycle events. Must be placed early in the pipeline so
 * downstream handlers can retrieve the context via {@code requireInteractionContext(ctx)}.
 */
@ChannelHandler.Sharable
public class InteractionHandler extends ChannelDuplexHandler {

    private static final Logger logger = LoggerFactory.getLogger(InteractionHandler.class);
    public static final AttributeKey<@Nullable InteractionContext<Session>> INTERACTION_CONTEXT_KEY =
            AttributeKey.valueOf("interactionContext");

    private final ContextProvider contextProvider;

    public InteractionHandler(ContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.trace("New connection from {}", ctx.channel().remoteAddress());
        ctx.fireChannelActive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        switch (evt) {
            case InteractionEvent.OperationStarted os -> {
                var ic = ctx.channel().attr(INTERACTION_CONTEXT_KEY).get();
                if (ic == null) break;
                if (os.session() != null && ic.session() == null) {
                    ic.setSession(os.session());
                }
                ic.setLifecycle(Lifecycle.OPERATION);
                logger.debug("Interaction: INITIALIZATION → OPERATION");
            }
            case InteractionEvent.ShutdownStarted ignored -> {
                var ic = ctx.channel().attr(INTERACTION_CONTEXT_KEY).get();
                if (ic == null) break;
                ic.setLifecycle(Lifecycle.SHUTDOWN);
                logger.debug("Interaction: OPERATION → SHUTDOWN");
            }
            case InteractionEvent.ShutdownComplete() -> {
                if (ctx.channel().hasAttr(INTERACTION_CONTEXT_KEY)) {
                    ctx.channel().attr(INTERACTION_CONTEXT_KEY).set(null);
                }
            }
            default -> {}
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest request) {
            var attr = ctx.channel().attr(INTERACTION_CONTEXT_KEY);
            if (attr.get() == null) {
                Protocols.resolve(request).ifPresent(proto -> {
                    attr.setIfAbsent(proto.createInteractionContext(contextProvider));
                    logger.debug("Protocol negotiated: {}:{}", proto.familyName(), proto.versionString());
                });
            }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.fireUserEventTriggered(InteractionEvent.ShutdownComplete.INSTANCE);
        ctx.close(promise);
    }
}
