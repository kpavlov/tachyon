// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.internal.ServerEngine
import dev.tachyonmcp.server.session.DefaultDispatchContext

/**
 * Builds a throwaway server and hands its stateless [InteractionContext] to [block],
 * closing the server afterward. Shared by tests that only need a context to drive a
 * handler directly, without binding a port.
 */
internal fun <T> withStatelessContext(block: (InteractionContext) -> T): T =
    TachyonServer.builder().build().use { server ->
        block(DefaultDispatchContext.stateless(server as ServerEngine))
    }
