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

/**
 * Creates a [PromptArgument] describing an argument accepted by a prompt template.
 *
 * @param name        argument name (matched against prompt call arguments)
 * @param title       human-readable title; null to omit
 * @param description description of the argument; null to omit
 * @param required    whether the argument must be provided; null = optional
 * @author Konstantin Pavlov
 */
public fun PromptArgument(
    name: String,
    title: String? = null,
    description: String? = null,
    required: Boolean? = null,
): PromptArgument = PromptArgument.of(name, title, description, required)
