// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.domain.PromptArgument
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.kotlin.domain.PromptArgument
import dev.tachyonmcp.server.kotlin.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.kotlin.features.prompts.promptMessagesOf

/** Simplest — name + description. */
fun simpleDescriptor(): PromptDescriptor =
    PromptDescriptor {
        name = "rewrite-forecast"
        description = "Rewrites a weather forecast in a given style"
    }

/** DSL — all properties. */
fun argDescriptor(): PromptDescriptor =
    PromptDescriptor {
        name = "rewrite"
        description = "Rewrites text in a style"
        title = "Rewrite Tool"
        argument {
            name = "text"
            description = "Original text"
            required = true
        }
        argument {
            name = "style"
            description = "Desired writing style"
            required = false
        }
        // inputSchema also settable
    }

/** Receiver factory with required and optional properties. */
fun singleArg(): PromptArgument =
    PromptArgument {
        name = "city"
        description = "City name"
        required = true
    }

/** Receiver factory with all properties shown. */
fun dslArg(): PromptArgument =
    PromptArgument {
        name = "country"
        title = "Country"
        description = "Country code (ISO)"
        required = true
    }

fun argHandler(args: String?): List<PromptMessage> {
    val text = args ?: "default text"
    return promptMessagesOf(PromptMessage.user("Rewrite this: $text"))
}
