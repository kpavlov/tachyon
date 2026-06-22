/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.runtime.DefaultInteractionContext;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.runtime.InteractionContext.Lifecycle;
import dev.tachyonmcp.runtime.InteractionEvent;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and manages the per-channel {@link InteractionContext} on channel
 * active/inactive lifecycle events. Must be placed early in the pipeline so
 * downstream handlers can retrieve the context via {@code interactionContext(ctx)}.
 */
@ChannelHandler.Sharable
public class InteractionHandler extends ChannelDuplexHandler {

    private static final Logger logger = LoggerFactory.getLogger(InteractionHandler.class);
    public static final AttributeKey<@Nullable InteractionContext> INTERACTION_CONTEXT_KEY =
            AttributeKey.valueOf("interactionContext");

    private final String protocol;

    public InteractionHandler(String protocol) {
        this.protocol = protocol;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public void channelActive(ChannelHandlerContext ctx) {
        Attribute<InteractionContext> attr = ctx.channel().attr(INTERACTION_CONTEXT_KEY);
        if (attr.compareAndSet(null, new DefaultInteractionContext<>(protocol))) {
            logger.debug("Interaction started: protocol={}", protocol);
        }
        ctx.fireChannelActive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        switch (evt) {
            case InteractionEvent.OperationStarted os -> {
                var ic = ctx.channel().attr(INTERACTION_CONTEXT_KEY).get();
                if (ic == null) break;
                if (os.session() != null) {
                    ic.setSession(os.session());
                }
                ic.setLifecycle(Lifecycle.OPERATION);
                logger.debug("Interaction: INITIALIZATION → OPERATION");
            }
            case InteractionEvent.ShutdownStarted ss -> {
                var ic = ctx.channel().attr(INTERACTION_CONTEXT_KEY).get();
                if (ic == null) break;
                ic.setLifecycle(Lifecycle.SHUTDOWN);
                logger.debug("Interaction: OPERATION → SHUTDOWN");
            }
            case InteractionEvent.ShutdownComplete() -> {
                ctx.channel().attr(INTERACTION_CONTEXT_KEY).set(null);
            }
            default -> {}
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.fireUserEventTriggered(new InteractionEvent.ShutdownComplete());
        ctx.close(promise);
    }
}
