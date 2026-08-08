// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.api.server.domain.Annotations
import dev.tachyonmcp.api.server.domain.Icon
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.core.server.features.resources.MimeTypes
import dev.tachyonmcp.kotlin.server.TachyonDsl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@TachyonDsl
public class ResourceDescriptorScope
    @PublishedApi
    internal constructor() {
        public var name: String? = null
        public var uri: String? = null
        public var description: String? = null

        /** Resource MIME type. Defaults to a guess from [uri]'s extension when left unset. */
        public var mimeType: String? = null
        public var title: String? = null
        public var annotations: Annotations? = null
        public var size: Long? = null
        public var icons: List<Icon> = emptyList()
        public var meta: Map<String, Any>? = null

        @PublishedApi
        internal fun build(): ResourceDescriptor {
            val n = requireNotNull(name) { "ResourceDescriptor.name is required" }
            val u = requireNotNull(uri) { "ResourceDescriptor.uri is required" }
            return ResourceDescriptor(
                name = n,
                uri = u,
                description = description,
                mimeType = mimeType ?: MimeTypes.guess(u),
                title = title,
                annotations = annotations,
                size = size,
                icons = icons,
                meta = meta,
            )
        }
    }

@OptIn(ExperimentalContracts::class)
public inline fun resourceDescriptor(
    name: String,
    uri: String,
    configure: ResourceDescriptorScope.() -> Unit = {},
): ResourceDescriptor {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return ResourceDescriptorScope()
        .apply {
            this.name = name
            this.uri = uri
            configure()
        }.build()
}

/** Builds a [ResourceDescriptor] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun ResourceDescriptor(
    block: ResourceDescriptorScope.() -> Unit,
): ResourceDescriptor {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ResourceDescriptorScope().apply(block).build()
}
