// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

import dev.tachyonmcp.server.TachyonDsl
import tools.jackson.databind.JsonNode

/** Builds [Annotations]. */
@TachyonDsl
public class AnnotationsBuilder
    @PublishedApi
    internal constructor() {
        /** Intended roles, or `null` when unrestricted. */
        public var audience: List<Role>? = null

        /** Ordering hint, or `null` for the default. */
        public var priority: Double? = null

        /** RFC-3339 modification timestamp, or `null` when unknown. */
        public var lastModified: String? = null

        @PublishedApi
        internal fun build(): Annotations = Annotations.of(audience, priority, lastModified)
    }

/** Builds an [Icon]. */
@TachyonDsl
public class IconBuilder
    @PublishedApi
    internal constructor() {
        /** Image URL or data URI. */
        public var src: String? = null

        /** Image MIME type. */
        public var mimeType: String? = null

        /** Conventional image sizes. */
        public var sizes: List<String>? = null

        /** Theme variant. */
        public var theme: String? = null

        @PublishedApi
        internal fun build(): Icon =
            Icon.of(
                requireNotNull(src) { "Icon.src is required" },
                mimeType,
                sizes,
                theme,
            )
    }

/** Builds a [PromptArgument]. */
@TachyonDsl
public class PromptArgumentBuilder
    @PublishedApi
    internal constructor() {
        /** Argument name. */
        public var name: String? = null

        /** Human-readable title. */
        public var title: String? = null

        /** Argument description. */
        public var description: String? = null

        /** Whether the argument is required. */
        public var required: Boolean? = null

        @PublishedApi
        internal fun build(): PromptArgument =
            PromptArgument.of(
                requireNotNull(name) { "PromptArgument.name is required" },
                title,
                description,
                required,
            )
    }

/** Builds [ToolAnnotations]. */
@TachyonDsl
public class ToolAnnotationsBuilder
    @PublishedApi
    internal constructor() {
        /** Human-readable tool title. */
        public var title: String? = null

        /** Whether the tool avoids state changes. */
        public var readOnlyHint: Boolean? = null

        /** Whether the tool may cause irreversible changes. */
        public var destructiveHint: Boolean? = null

        /** Whether retrying with the same arguments is safe. */
        public var idempotentHint: Boolean? = null

        /** Whether the tool may interact with external systems. */
        public var openWorldHint: Boolean? = null

        @PublishedApi
        internal fun build(): ToolAnnotations =
            ToolAnnotations.of(
                title,
                readOnlyHint,
                destructiveHint,
                idempotentHint,
                openWorldHint,
            )
    }

/** Builds [ImageContent]. */
@TachyonDsl
public class ImageContentBuilder
    @PublishedApi
    internal constructor() {
        /** Base64-encoded image bytes. */
        public var data: String? = null

        /** Image MIME type. */
        public var mimeType: String? = null

        /** Optional presentation hints. */
        public var annotations: Annotations? = null

        /** Optional content metadata. */
        public var meta: Map<String, JsonNode>? = null

        @PublishedApi
        internal fun build(): ImageContent =
            ImageContent.of(
                requireNotNull(data) { "ImageContent.data is required" },
                requireNotNull(mimeType) { "ImageContent.mimeType is required" },
                annotations,
                meta,
            )
    }

/** Builds [AudioContent]. */
@TachyonDsl
public class AudioContentBuilder
    @PublishedApi
    internal constructor() {
        /** Base64-encoded audio bytes. */
        public var data: String? = null

        /** Audio MIME type. */
        public var mimeType: String? = null

        /** Optional presentation hints. */
        public var annotations: Annotations? = null

        /** Optional content metadata. */
        public var meta: Map<String, JsonNode>? = null

        @PublishedApi
        internal fun build(): AudioContent =
            AudioContent.of(
                requireNotNull(data) { "AudioContent.data is required" },
                requireNotNull(mimeType) { "AudioContent.mimeType is required" },
                annotations,
                meta,
            )
    }

/** Builds [BlobResourceContents]. */
@TachyonDsl
public class BlobResourceContentsBuilder
    @PublishedApi
    internal constructor() {
        /** Resource URI. */
        public var uri: String? = null

        /** Base64-encoded resource bytes. */
        public var blob: String? = null

        /** Resource MIME type. */
        public var mimeType: String? = null

        /** Optional resource metadata. */
        public var meta: Map<String, JsonNode> = emptyMap()

        @PublishedApi
        internal fun build(): BlobResourceContents =
            BlobResourceContents.of(
                requireNotNull(uri) { "BlobResourceContents.uri is required" },
                requireNotNull(blob) { "BlobResourceContents.blob is required" },
                mimeType,
                meta,
            )
    }
