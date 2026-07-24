@file:Suppress("FunctionName")
@file:JvmName("ResourceContentsFactory")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

import dev.tachyonmcp.server.kotlin.json.toJacksonNodeMap
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Builds [TextResourceContents] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun TextResourceContents(
    block: TextResourceContentsBuilder.() -> Unit,
): TextResourceContents {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return TextResourceContentsBuilder().apply(block).build()
}

/** Builds [BlobResourceContents] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun BlobResourceContents(
    block: BlobResourceContentsBuilder.() -> Unit,
): BlobResourceContents {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return BlobResourceContentsBuilder().apply(block).build()
}

/**
 * Creates [TextResourceContents] — text-based resource data returned by a handler.
 *
 * @param uri      originating resource URI
 * @param text     the actual text content
 * @param mimeType MIME type of the text (e.g. "application/json"); null to omit
 * @param meta     optional request-level metadata; null to omit
 * @author Konstantin Pavlov
 */
public fun TextResourceContents(
    uri: String,
    text: String,
    mimeType: String? = null,
    meta: Map<String, JsonNode>? = null,
): TextResourceContents = TextResourceContents.of(uri, text, mimeType, meta)

/**
 * Creates [TextResourceContents] using a kotlinx-serialization metadata map.
 * Requires kotlinx-serialization-json on the classpath.
 *
 * @author Konstantin Pavlov
 */
@JvmName("textResourceContentsWithKxMeta")
public fun TextResourceContents(
    uri: String,
    text: String,
    mimeType: String? = null,
    meta: Map<String, JsonObject>?,
): TextResourceContents = TextResourceContents.of(uri, text, mimeType, meta?.toJacksonNodeMap())

/**
 * Creates [BlobResourceContents] — binary resource data encoded as base64.
 *
 * @param uri      originating resource URI
 * @param blob     base64-encoded binary content
 * @param mimeType MIME type of the binary data; null to omit
 * @param meta     optional request-level metadata; defaults to empty map
 * @author Konstantin Pavlov
 */
public fun BlobResourceContents(
    uri: String,
    blob: String,
    mimeType: String? = null,
    meta: Map<String, JsonNode> = emptyMap(),
): BlobResourceContents = BlobResourceContents.of(uri, blob, mimeType, meta)

/**
 * Creates [BlobResourceContents] using a kotlinx-serialization metadata map.
 * Requires kotlinx-serialization-json on the classpath.
 *
 * @author Konstantin Pavlov
 */
@JvmName("blobResourceContentsWithKxMeta")
public fun BlobResourceContents(
    uri: String,
    blob: String,
    mimeType: String? = null,
    meta: Map<String, JsonObject>,
): BlobResourceContents = BlobResourceContents.of(uri, blob, mimeType, meta.toJacksonNodeMap())
