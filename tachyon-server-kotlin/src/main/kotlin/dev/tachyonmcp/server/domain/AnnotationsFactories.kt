@file:Suppress("FunctionName")
@file:JvmName("AnnotationsFactory")

// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.domain

/**
 * Creates [Annotations] — optional metadata for tailoring content presentation.
 *
 * @param audience     intended roles (user, assistant); null when unrestricted
 * @param priority     ordering hint in [0.0, 1.0]; null for default
 * @param lastModified RFC-3339 timestamp of last modification; null when unknown
 */
public fun Annotations(
    audience: List<Role>? = null,
    priority: Double? = null,
    lastModified: String? = null,
): Annotations = Annotations.of(audience, priority, lastModified)
