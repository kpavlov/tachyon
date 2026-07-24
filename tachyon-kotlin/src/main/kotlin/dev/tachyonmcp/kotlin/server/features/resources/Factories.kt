@file:Suppress("FunctionName")
@file:JvmName("ResourceDescriptors")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.server.features.resources.ResourceDescriptor

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
 */
public fun ResourceDescriptor(
    name: String,
    uri: String,
    description: String? = null,
    mimeType: String? = null,
    title: String? = null,
    annotations: dev.tachyonmcp.server.domain.Annotations? = null,
    size: Long? = null,
    icons: List<dev.tachyonmcp.server.domain.Icon>? = null,
): ResourceDescriptor =
    ResourceDescriptor.of(
        name,
        uri,
        description,
        mimeType,
        title,
        annotations,
        size,
        icons,
    )
