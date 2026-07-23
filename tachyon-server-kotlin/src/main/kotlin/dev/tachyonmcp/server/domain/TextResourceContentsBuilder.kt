// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.config.ResourceScope
import dev.tachyonmcp.server.config.TemplateScope
import tools.jackson.databind.JsonNode

/**
 * Builds [TextResourceContents] for a matched resource template.
 *
 * The requested URI and registered template MIME type are used as defaults.
 */
@TachyonDsl
public class TextResourceContentsBuilder
    @PublishedApi
    internal constructor(
        private val scope: ResourceScope? = null,
    ) {
        /** Resource URI. Defaults to the requested URI. */
        public var uri: String? = scope?.uri

        /** Resource MIME type. Defaults to the registered template MIME type. */
        public var mimeType: String? = scope?.registeredMimeType

        /** Text payload. */
        public var text: String? = null

        /** Optional resource metadata. */
        public var meta: Map<String, JsonNode>? = null

        /**
         * Returns a scalar URI-template parameter.
         *
         * @param name template parameter name
         */
        public fun param(name: String): String =
            requireNotNull(scope as? TemplateScope) {
                "URI-template parameters require a TemplateScope"
            }.param(name)

        /**
         * Returns a sequence URI-template parameter.
         *
         * @param name template parameter name
         */
        public fun sequence(name: String): List<String> =
            requireNotNull(
                scope as? TemplateScope,
            ) { "URI-template parameters require a TemplateScope" }.sequence(name)

        @PublishedApi
        internal fun build(): TextResourceContents =
            TextResourceContents.of(
                requireNotNull(uri) { "TextResourceContents.uri is required" },
                requireNotNull(text) { "TextResourceContents.text is required" },
                mimeType,
                meta,
            )
    }
