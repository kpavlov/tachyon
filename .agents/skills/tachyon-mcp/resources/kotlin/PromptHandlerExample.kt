// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.domain.PromptArgument
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.promptMessagesOf
import dev.tachyonmcp.server.promptArgument
import dev.tachyonmcp.server.promptDescriptor

/**
 * Demonstrates prompt descriptor construction patterns.
 */

/** Simplest — name + description. */
fun simpleDescriptor(): PromptDescriptor =
    PromptDescriptor.of("rewrite-forecast", "Rewrites a weather forecast in a given style")

/** With typed arguments. */
fun argDescriptor(): PromptDescriptor =
    promptDescriptor("rewrite") {
        description = "Rewrites text in a style"
        title = "Rewrite Tool"
        arguments = listOf(
            promptArgument(name = "text", description = "Original text", required = true),
            promptArgument(name = "style", description = "Desired writing style", required = false),
        )
    }

/** Handler that reads the argument. */
fun argHandler(args: String?): List<PromptMessage> {
    val text = args ?: "default text"
    return promptMessagesOf(PromptMessage.user("Rewrite this: $text"))
}
