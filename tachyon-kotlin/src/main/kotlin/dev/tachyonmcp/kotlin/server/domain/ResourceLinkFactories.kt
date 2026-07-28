// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
@file:Suppress("FunctionName")
@file:JvmName("ResourceLinks")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.domain

import dev.tachyonmcp.api.server.domain.ResourceLink

/**
 * Creates a [ResourceLink] — a lightweight pointer to another resource.
 *
 * @param uri     target resource URI
 * @param name    display name for the link
 * @param mimeType MIME type of the target; null when unknown
 */
public fun ResourceLink(
    uri: String,
    name: String,
    mimeType: String? = null,
): ResourceLink =
    ResourceLink
        .of(uri, name, mimeType)
