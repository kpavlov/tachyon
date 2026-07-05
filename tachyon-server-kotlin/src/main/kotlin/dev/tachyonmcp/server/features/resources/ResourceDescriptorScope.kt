// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.features.resources

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
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
        public var mimeType: String? = null
        public var title: String? = null
        public var annotations: Annotations? = null
        public var size: Double? = null
        public var icons: List<Icon>? = null

        @PublishedApi
        internal fun build(): ResourceDescriptor {
            val n = requireNotNull(name) { "ResourceDescriptor.name is required" }
            val u = requireNotNull(uri) { "ResourceDescriptor.uri is required" }
            return ResourceDescriptor(
                name = n,
                uri = u,
                description = description,
                mimeType = mimeType,
                title = title,
                annotations = annotations,
                size = size,
                icons = icons,
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
