@file:Suppress("FunctionName")
@file:JvmName("Icons")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

/**
 * Creates an [Icon] pointing to an image resource.
 *
 * @param src     image URL or data URI
 * @param mimeType image MIME type (e.g. "image/png"); null when unknown
 * @param sizes   conventional size labels (e.g. ["16x16", "32x32"]); null when unspecified
 * @param theme   theme variant ("light", "dark"); null when universal
 */
public fun Icon(
    src: String,
    mimeType: String? = null,
    sizes: List<String>? = null,
    theme: String? = null,
): Icon = DefaultIcon.of(src, mimeType, sizes, theme)
