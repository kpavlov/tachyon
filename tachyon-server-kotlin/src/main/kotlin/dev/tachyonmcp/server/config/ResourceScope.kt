// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.UriTemplateValue
import dev.tachyonmcp.server.features.resources.ResourceRequest

/**
 * Receiver for a static or templated resource handler.
 */
@TachyonDsl
public class ResourceScope
    internal constructor(
        /** Interaction context for the resource read. */
        public val ctx: InteractionContext,
        /** Full resource request, including URI-template data and request metadata. */
        public val request: ResourceRequest,
    ) {
        /** Requested resource URI. */
        public val uri: String
            get() = request.uri()

        /** Parsed URI-template parameters, or an empty map for a static resource. */
        public val params: Map<String, UriTemplateValue>
            get() = request.params()

        /** Original URI template, or `null` for a static resource. */
        public val uriTemplate: String?
            get() = request.uriTemplate()
    }
