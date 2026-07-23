@file:Suppress("FunctionName")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.features.resources

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Builds a [ResourceTemplateDescriptor]. */
@TachyonDsl
public class ResourceTemplateDescriptorBuilder
    @PublishedApi
    internal constructor() {
        /** Template name. */
        public var name: String? = null

        /** URI-template pattern. */
        public var uriTemplate: String? = null

        /** Template description. */
        public var description: String? = null

        /** Resource MIME type. */
        public var mimeType: String? = null

        /** Human-readable title. */
        public var title: String? = null

        /** Optional presentation hints. */
        public var annotations: Annotations? = null

        /** Associated icons. */
        public var icons: List<Icon>? = null

        @PublishedApi
        internal fun build(): ResourceTemplateDescriptor =
            ResourceTemplateDescriptor.of(
                requireNotNull(name) { "ResourceTemplateDescriptor.name is required" },
                requireNotNull(
                    uriTemplate,
                ) { "ResourceTemplateDescriptor.uriTemplate is required" },
                description,
                mimeType,
                title,
                annotations,
                icons,
            )
    }

/** Builds a [ResourceTemplateDescriptor] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun ResourceTemplateDescriptor(
    block: ResourceTemplateDescriptorBuilder.() -> Unit,
): ResourceTemplateDescriptor {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ResourceTemplateDescriptorBuilder().apply(block).build()
}
