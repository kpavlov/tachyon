// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.domain.PromptArgument
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.features.prompts.promptArgument
import dev.tachyonmcp.server.features.prompts.promptDescriptor
import dev.tachyonmcp.server.features.prompts.promptMessagesOf

/** Simplest — name + description. */
fun simpleDescriptor(): PromptDescriptor =
    PromptDescriptor.of("rewrite-forecast", "Rewrites a weather forecast in a given style")

/** DSL — all properties. */
fun argDescriptor(): PromptDescriptor =
    promptDescriptor("rewrite") {
        description = "Rewrites text in a style"
        title = "Rewrite Tool"
        arguments = listOf(
            promptArgument(name = "text", description = "Original text", required = true),
            promptArgument(name = "style", description = "Desired writing style", required = false),
        )
        // inputSchema also settable
    }

/** No-arg convenience: promptArgument(name, description, required). */
fun singleArg(): PromptArgument =
    promptArgument(name = "city", description = "City name", required = true)

/** DSL builder for promptArgument — all properties shown. */
fun dslArg(): PromptArgument =
    promptArgument {
        name = "country"
        title = "Country"
        description = "Country code (ISO)"
        required = true
    }

fun argHandler(args: String?): List<PromptMessage> {
    val text = args ?: "default text"
    return promptMessagesOf(PromptMessage.user("Rewrite this: $text"))
}
