// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.prompts

import dev.tachyonmcp.protocol.api.server.domain.PromptMessage

public fun promptMessagesOf(vararg messages: PromptMessage): List<PromptMessage> = messages.toList()
