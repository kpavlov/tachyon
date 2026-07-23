// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.BlobResourceContents
import dev.tachyonmcp.server.domain.BlobResourceContentsBuilder
import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.domain.TextResourceContentsBuilder
import dev.tachyonmcp.server.domain.UriTemplateValue
import dev.tachyonmcp.server.features.resources.ResourceRequest
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Receiver for a static or templated resource handler.
 */
@TachyonDsl
public open class ResourceScope
    internal constructor(
        /** Interaction context for the resource read. */
        public val ctx: InteractionContext,
        /** Full resource request, including URI-template data and request metadata. */
        public val request: ResourceRequest,
        internal val registeredMimeType: String?,
    ) {
        /** Requested resource URI. */
        public val uri: String
            get() = request.uri()

        /** Parsed URI-template parameters, or an empty map for a static resource. */
        public val params: Map<String, UriTemplateValue>
            get() = request.params()

        /** Original URI template, or `null` for a static resource. */
        public open val uriTemplate: String?
            get() = request.uriTemplate()

        /**
         * Builds text resource contents using this request's URI and the registered MIME type as
         * defaults.
         */
        @OptIn(ExperimentalContracts::class)
        public inline fun TextResourceContents(
            block: (@TachyonDsl TextResourceContentsBuilder).() -> Unit,
        ): TextResourceContents {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            return TextResourceContentsBuilder(this).apply(block).build()
        }

        /**
         * Builds binary resource contents using this request's URI and the registered MIME type as
         * defaults.
         */
        @OptIn(ExperimentalContracts::class)
        public inline fun BlobResourceContents(
            block: (@TachyonDsl BlobResourceContentsBuilder).() -> Unit,
        ): BlobResourceContents {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            return BlobResourceContentsBuilder(this).apply(block).build()
        }
    }
