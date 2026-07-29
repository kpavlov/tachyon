// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
@file:Suppress("FunctionName")
@file:JvmName("ResourceDescriptors")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.api.server.domain.Annotations
import dev.tachyonmcp.api.server.domain.Icon
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor

/**
 * Creates a [ResourceDescriptor] describing a static resource.
 *
 * @param name        resource name (used for lookup)
 * @param uri         resource URI
 * @param description description of the resource; null to omit
 * @param mimeType    MIME type of the resource content; null to omit
 * @param title       human-readable title; null to omit
 * @param annotations optional presentation hints
 * @param size        estimated size hint in bytes; null = unknown
 * @param icons       list of associated icons; null to omit
 * @param meta        protocol extension metadata; null to omit
 */
public fun ResourceDescriptor(
    name: String,
    uri: String,
    description: String? = null,
    mimeType: String? = null,
    title: String? = null,
    annotations: Annotations? = null,
    size: Long? = null,
    icons: List<Icon>? = null,
    meta: Map<String, Any>? = null,
): ResourceDescriptor =
    ResourceDescriptor
        .builder()
        .name(name)
        .uri(uri)
        .description(description)
        .mimeType(mimeType)
        .title(title)
        .annotations(annotations)
        .size(size)
        .icons(icons)
        .meta(meta)
        .build()
