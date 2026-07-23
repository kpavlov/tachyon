@file:Suppress("FunctionName")
@file:JvmName("PromptMessages")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [PromptMessage] associating a [Role] with its [ContentBlock].
 *
 * @param role    message role (user / assistant)
 * @param content the message content block
 * @author Konstantin Pavlov
 */
public fun PromptMessage(
    role: Role,
    content: ContentBlock,
): PromptMessage = PromptMessage.of(role, content)

/** Builds a [PromptArgument] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun PromptArgument(block: PromptArgumentBuilder.() -> Unit): PromptArgument {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return PromptArgumentBuilder().apply(block).build()
}
