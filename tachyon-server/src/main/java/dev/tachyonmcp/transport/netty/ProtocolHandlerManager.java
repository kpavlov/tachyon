/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import io.netty.channel.ChannelHandler;
import org.jspecify.annotations.Nullable;

public interface ProtocolHandlerManager {

    String initHandlerName();

    String operationHandlerName();

    ChannelHandler createOperationHandler();

    void onShutdownStarted(@Nullable String sessionId);
}
