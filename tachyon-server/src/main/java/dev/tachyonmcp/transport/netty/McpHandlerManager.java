/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import dev.tachyonmcp.server.RpcDispatcher;
import dev.tachyonmcp.server.Server;
import io.netty.channel.ChannelHandler;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

public class McpHandlerManager implements ProtocolHandlerManager {

    static final String HANDLER_INIT = "mcp-phase-init";
    static final String HANDLER_OPS = "mcp-phase-operations";

    private final Server server;
    private final RpcDispatcher dispatcher;
    private final Executor executor;

    public McpHandlerManager(Server server, RpcDispatcher dispatcher) {
        this(server, dispatcher, server.executor());
    }

    public McpHandlerManager(Server server, RpcDispatcher dispatcher, Executor executor) {
        this.server = server;
        this.dispatcher = dispatcher;
        this.executor = executor;
    }

    @Override
    public String initHandlerName() {
        return HANDLER_INIT;
    }

    @Override
    public String operationHandlerName() {
        return HANDLER_OPS;
    }

    @Override
    public ChannelHandler createOperationHandler() {
        return new McpOperationHandler(server, dispatcher, executor);
    }

    @Override
    public void onShutdownStarted(@Nullable String sessionId) {
        if (sessionId != null) {
            server.removeSession(sessionId);
        }
    }
}
