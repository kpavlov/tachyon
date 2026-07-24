// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.kotlin.features.prompts

public fun promptMessagesOf(
    vararg messages: dev.tachyonmcp.server.domain.PromptMessage,
): List<dev.tachyonmcp.server.domain.PromptMessage> = messages.toList()
