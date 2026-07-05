@file:Suppress("FunctionName")
@file:JvmName("ResourceContentsFactory")

// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.domain

import dev.tachyonmcp.server.json.toJacksonNodeMap
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

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
): TextResourceContents = TextResourceContents.of(uri, mimeType, text, meta)

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
    meta: Map<String, JsonObject>? = null,
): TextResourceContents = TextResourceContents.of(uri, mimeType, text, meta?.toJacksonNodeMap())

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
): BlobResourceContents = BlobResourceContents.of(uri, mimeType, blob, meta)

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
    meta: Map<String, JsonObject> = emptyMap(),
): BlobResourceContents = BlobResourceContents.of(uri, mimeType, blob, meta.toJacksonNodeMap())

/**
 * Creates a [ReadResourceRequest] — a request to read a resource by URI.
 *
 * @param uri  the resource URI to read
 * @param meta optional request-level metadata forwarded to the handler
 * @author Konstantin Pavlov
 */
public fun ReadResourceRequest(
    uri: String,
    meta: Map<String, JsonNode>? = null,
): ReadResourceRequest = ReadResourceRequest.of(uri, meta)

/**
 * Creates a [ReadResourceRequest] using a kotlinx-serialization metadata map.
 * Requires kotlinx-serialization-json on the classpath.
 *
 * @author Konstantin Pavlov
 */
@JvmName("readResourceRequestWithKxMeta")
public fun ReadResourceRequest(
    uri: String,
    meta: Map<String, JsonObject>?,
): ReadResourceRequest = ReadResourceRequest.of(uri, meta?.toJacksonNodeMap())
