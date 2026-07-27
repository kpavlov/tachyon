// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.tools

import dev.tachyonmcp.kotlin.server.config.ToolScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.protocol.api.server.features.tools.AsyncToolFn
import dev.tachyonmcp.protocol.api.server.features.tools.ToolDescriptor
import dev.tachyonmcp.protocol.api.server.features.tools.ToolHandler
import dev.tachyonmcp.protocol.api.server.features.tools.ToolResult
import kotlinx.coroutines.CoroutineName

@JvmSynthetic
internal fun toolHandler(
    descriptor: ToolDescriptor,
    runtime: CoroutineRuntime,
    block: suspend ToolScope.() -> ToolResult,
): ToolHandler =
    ToolHandler.ofAsync(
        descriptor,
        toolFn(descriptor.name(), runtime, block),
    )

@JvmSynthetic
internal fun toolFn(
    name: String,
    runtime: CoroutineRuntime,
    block: suspend ToolScope.() -> ToolResult,
): AsyncToolFn {
    val coroutineName = CoroutineName("tool:$name")
    return AsyncToolFn { context, request ->
        runtime.future(coroutineName) {
            ToolScope(
                ctx = context,
                request = request,
            ).block()
        }
    }
}
