// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.UriTemplateValue
import dev.tachyonmcp.server.features.resources.ResourceRequest

/**
 * Receiver for a matched resource-template handler.
 */
@TachyonDsl
public class TemplateScope
    internal constructor(
        /** Interaction context for the resource read. */
        public val ctx: InteractionContext,
        /** Full resource request, including URI-template data and request metadata. */
        public val request: ResourceRequest,
    ) {
        /** Requested resource URI. */
        public val uri: String
            get() = request.uri()

        /** Parsed URI-template parameters for the match. */
        public val params: Map<String, UriTemplateValue>
            get() = request.params()

        /** Original URI template, always present for a template match. */
        public val uriTemplate: String
            get() = requireNotNull(request.uriTemplate())

        /**
         * Retrieves a scalar template variable.
         *
         * @param name The name of the template variable.
         * @return The template variable's scalar value.
         * @throws IllegalArgumentException If the variable is missing or contains a sequence.
         */
        public fun param(name: String): String =
            when (val value = value(name)) {
                is UriTemplateValue.Scalar -> value.value()
                is UriTemplateValue.Sequence ->
                    throw IllegalArgumentException("template variable is not scalar: $name")
            }

        /**
         * Retrieves a sequence template variable.
         *
         * @param name The name of the template variable.
         * @return The values in the template variable.
         * @throws IllegalArgumentException If the variable is missing or is scalar.
         */
        public fun sequence(name: String): List<String> =
            when (val value = value(name)) {
                is UriTemplateValue.Scalar ->
                    throw IllegalArgumentException("template variable is not a sequence: $name")
                is UriTemplateValue.Sequence -> value.values()
            }

        /**
         * Retrieves a template variable by name.
         *
         * @param name The template variable name.
         * @return The template variable value.
         * @throws IllegalArgumentException If the template variable is missing.
         */
        private fun value(name: String): UriTemplateValue =
            params[name] ?: throw IllegalArgumentException("missing template variable: $name")
    }
