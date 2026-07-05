@file:Suppress("FunctionName")
@file:JvmName("ContentBlocks")

// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.domain

import dev.tachyonmcp.server.json.toJacksonNodeMap
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

/**
 * Creates a [TextContent] block — plain text provided to or from an LLM.
 *
 * @param text        the text content
 * @param meta        optional request-level metadata; null to omit
 * @param annotations optional presentation hints (audience, priority, etc.)
 */
public fun TextContent(
    text: String,
    meta: Map<String, JsonNode>? = null,
    annotations: Annotations? = null,
): TextContent = TextContent.of(text, meta, annotations)

/**
 * Creates a [TextContent] block using a kotlinx-serialization metadata map.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmName("textContentWithKxMeta")
public fun TextContent(
    text: String,
    meta: Map<String, JsonObject>?,
    annotations: Annotations? = null,
): TextContent = TextContent.of(text, meta?.toJacksonNodeMap(), annotations)

/**
 * Creates an [ImageContent] block — base64-encoded image data.
 *
 * @param data        base64-encoded image bytes
 * @param mimeType    image format (e.g. "image/png")
 * @param annotations optional presentation hints
 * @param meta        optional request-level metadata; null to omit
 */
public fun ImageContent(
    data: String,
    mimeType: String,
    annotations: Annotations? = null,
    meta: Map<String, JsonNode>? = null,
): ImageContent = ImageContent.of(data, mimeType, annotations, meta)

/**
 * Creates an [ImageContent] block using a kotlinx-serialization metadata map.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmName("imageContentWithKxMeta")
public fun ImageContent(
    data: String,
    mimeType: String,
    annotations: Annotations? = null,
    meta: Map<String, JsonObject>?,
): ImageContent = ImageContent.of(data, mimeType, annotations, meta?.toJacksonNodeMap())

/**
 * Creates an [AudioContent] block — base64-encoded audio data.
 *
 * @param data        base64-encoded audio bytes
 * @param mimeType    audio format (e.g. "audio/mp3")
 * @param annotations optional presentation hints
 * @param meta        optional request-level metadata; null to omit
 */
public fun AudioContent(
    data: String,
    mimeType: String,
    annotations: Annotations? = null,
    meta: Map<String, JsonNode>? = null,
): AudioContent = DefaultAudioContent.of(data, mimeType, annotations, meta)

/**
 * Creates an [AudioContent] block using a kotlinx-serialization metadata map.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmName("audioContentWithKxMeta")
public fun AudioContent(
    data: String,
    mimeType: String,
    annotations: Annotations? = null,
    meta: Map<String, JsonObject>?,
): AudioContent = DefaultAudioContent.of(data, mimeType, annotations, meta?.toJacksonNodeMap())

/**
 * Creates an [EmbeddedResource] — a complete resource inlined within a result.
 *
 * @param resource    the resource contents (text or blob)
 * @param annotations optional presentation hints
 * @param meta        optional request-level metadata; null to omit
 */
public fun EmbeddedResource(
    resource: ResourceContents,
    annotations: Annotations? = null,
    meta: Map<String, JsonNode>? = null,
): EmbeddedResource = EmbeddedResource.of(resource, annotations, meta)

/**
 * Creates an [EmbeddedResource] using a kotlinx-serialization metadata map.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmName("embeddedResourceWithKxMeta")
public fun EmbeddedResource(
    resource: ResourceContents,
    annotations: Annotations? = null,
    meta: Map<String, JsonObject>?,
): EmbeddedResource = EmbeddedResource.of(resource, annotations, meta?.toJacksonNodeMap())
