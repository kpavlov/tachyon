// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.domain.PromptMessage

public fun promptMessagesOf(vararg messages: PromptMessage): List<PromptMessage> = messages.toList()
