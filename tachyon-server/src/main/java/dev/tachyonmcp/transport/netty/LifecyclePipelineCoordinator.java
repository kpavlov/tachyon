/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.runtime.InteractionEvent;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class LifecyclePipelineCoordinator extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(LifecyclePipelineCoordinator.class);

    private final ProtocolHandlerManager manager;

    public LifecyclePipelineCoordinator(ProtocolHandlerManager manager) {
        this.manager = manager;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        switch (evt) {
            case InteractionEvent.OperationStarted os -> {
                logger.debug("Lifecycle: INITIALIZATION → OPERATION");
                ctx.pipeline()
                        .replace(
                                manager.initHandlerName(),
                                manager.operationHandlerName(),
                                manager.createOperationHandler());
            }
            case InteractionEvent.ShutdownStarted ss -> {
                logger.debug("Lifecycle: shutdown requested");
                manager.onShutdownStarted(ss.sessionId());
            }
            default -> {}
        }
        ctx.fireUserEventTriggered(evt);
    }
}
