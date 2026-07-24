// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.kotlin.domain

import dev.tachyonmcp.server.kotlin.TachyonDsl
import dev.tachyonmcp.server.kotlin.config.ResourceScope
import dev.tachyonmcp.server.kotlin.config.TemplateScope
import tools.jackson.databind.JsonNode

/**
 * Builds [dev.tachyonmcp.server.domain.TextResourceContents] for a matched resource template.
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
        internal fun build(): dev.tachyonmcp.server.domain.TextResourceContents =
            dev.tachyonmcp.server.domain.TextResourceContents.of(
                requireNotNull(uri) {
                    "TextResourceContents.uri is required: set it explicitly, or build inside a " +
                        "resource/template handler where TextResourceContents { } defaults it from the request"
                },
                requireNotNull(text) { "TextResourceContents.text is required" },
                mimeType,
                meta,
            )
    }
