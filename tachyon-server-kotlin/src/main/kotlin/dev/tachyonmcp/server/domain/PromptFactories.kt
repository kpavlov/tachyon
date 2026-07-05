@file:Suppress("FunctionName")
@file:JvmName("PromptMessages")

// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.domain

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
