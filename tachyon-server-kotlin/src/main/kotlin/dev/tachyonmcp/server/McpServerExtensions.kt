/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.DefaultToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import tools.jackson.databind.JsonNode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@JvmSynthetic
public fun McpServer.registerTool(
    name: String,
    description: String? = null,
    inputSchema: JsonNode? = null,
    block: suspend ToolScope.() -> ToolResult<*>,
): McpServer =
    registerTool(configure = {
        name(name)
        description(description)
        inputSchema(inputSchema)
    }, block = block)

@JvmSynthetic
@OptIn(ExperimentalContracts::class)
public inline fun McpServer.registerTool(
    configure: DefaultToolDescriptor.Builder.() -> Unit = {},
    noinline block: suspend ToolScope.() -> ToolResult<*>,
): McpServer {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return registerTool(
        descriptor = ToolDescriptor.builder().apply(configure).build(),
        block = block,
    )
}

@JvmSynthetic
public fun McpServer.registerTool(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult<*>,
): McpServer {
    this.registerTool(asyncHandler(descriptor, block))
    return this
}
