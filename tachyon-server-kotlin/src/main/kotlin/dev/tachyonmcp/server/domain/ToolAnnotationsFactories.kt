@file:Suppress("FunctionName")
@file:JvmName("ToolAnnotationsFactory")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

/**
 * Creates [ToolAnnotations] — hints about tool behaviour for safety or UX.
 *
 * @param title           human-readable tool title; null to omit
 * @param readOnlyHint    tool does not modify state; null = unknown
 * @param destructiveHint tool may cause irreversible changes; null = unknown
 * @param idempotentHint  safe to retry; null = unknown
 * @param openWorldHint   tool may reach outside MCP ecosystem; null = unknown
 */
public fun ToolAnnotations(
    title: String? = null,
    readOnlyHint: Boolean? = null,
    destructiveHint: Boolean? = null,
    idempotentHint: Boolean? = null,
    openWorldHint: Boolean? = null,
): ToolAnnotations =
    ToolAnnotations.of(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint)
