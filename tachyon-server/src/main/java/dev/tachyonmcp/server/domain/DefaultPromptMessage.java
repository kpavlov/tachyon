/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

record DefaultPromptMessage(Role role, ContentBlock content) implements PromptMessage {}
