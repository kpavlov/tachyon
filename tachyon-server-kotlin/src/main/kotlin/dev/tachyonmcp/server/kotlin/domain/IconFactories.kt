@file:Suppress("FunctionName")
@file:JvmName("Icons")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.kotlin.domain

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Builds an [Icon] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun Icon(block: IconBuilder.() -> Unit): dev.tachyonmcp.server.domain.Icon {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return IconBuilder().apply(block).build()
}

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
): dev.tachyonmcp.server.domain.Icon =
    dev.tachyonmcp.server.domain.Icon
        .of(src, mimeType, sizes, theme)
