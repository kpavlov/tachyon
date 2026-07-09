// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.features.prompts

import dev.tachyonmcp.server.domain.PromptMessage

public fun promptMessagesOf(vararg messages: PromptMessage): List<PromptMessage> = messages.toList()
